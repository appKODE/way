package ru.kode.way

internal fun resolveTransition(
  schema: Schema,
  regions: Map<RegionId, Region>,
  nodeBuilder: NodeBuilder,
  event: Event,
  extensionPoints: List<NodeExtensionPoint>,
): ResolvedTransition {
  return regions.entries.fold(ResolvedTransition.EMPTY) { acc, (regionId, region) ->
    val node = region.nodes[region.active] ?: error("expected node to exist at path \"${region.active}\"")
    val transition = buildTransition(event, node, region.active, extensionPoints)
    val resolved = resolveTransitionInRegion(
      schema = schema,
      regionId = regionId,
      transition,
      path = region.active,
      activePath = region.active,
      nodes = region.nodes,
      nodeBuilder = nodeBuilder,
      finishHandlers = region.finishHandlers,
      event = event,
      extensionPoints = extensionPoints,
    )
    ResolvedTransition(
      targetPaths = acc.targetPaths + resolved.targetPaths,
      finishHandlers = (acc.finishHandlers.orEmpty() + resolved.finishHandlers.orEmpty()).takeIf { it.isNotEmpty() },
      payloads = acc.payloads + resolved.payloads,
      enqueuedEvents = (acc.enqueuedEvents.orEmpty() + resolved.enqueuedEvents.orEmpty()).takeIf { it.isNotEmpty() },
    )
  }
}

/**
 * @param path A path relative to which resolution happens
 * @param activePath A path which was last active in navigation state
 */
private fun resolveTransitionInRegion(
  schema: Schema,
  regionId: RegionId,
  transition: Transition,
  path: Path,
  activePath: Path,
  nodes: Map<Path, Node>,
  nodeBuilder: NodeBuilder,
  finishHandlers: Map<Path, OnFinishHandler<Any, Any>>,
  event: Event,
  extensionPoints: List<NodeExtensionPoint>,
): ResolvedTransition {
  return when (transition) {
    is EnqueueEvent -> ResolvedTransition(
      targetPaths = mapOf(regionId to path),
      finishHandlers = null,
      payloads = emptyMap(),
      enqueuedEvents = listOf(transition.event),
    )
    is NavigateTo -> {
      var transitionFinishHandlers: HashMap<RegionId, ResolvedTransition.FinishHandler>? = null
      val targetPaths = HashMap<RegionId, Path>(transition.targets.size)
      val payloads = mutableMapOf<Path, Any>()
      transition.targets.forEach { target ->
        // there maybe several targets in different regions
        val targetRegionId = regionIdOfPath(schema.regions, target.path)
          ?: error("failed to find regionId for path=\"${target.path}\"")
        val targetPathAbs = schema.target(targetRegionId, target.path.segments.last())
          ?: error("failed to find schema entry for target \"${target.path}\"")
        target.payload?.also { payloads[targetPathAbs] = it }
        targetPaths[targetRegionId] = maybeResolveInitial(target, targetPathAbs, nodeBuilder, nodes, payloads)
        when (target) {
          is FlowTarget<*, *> -> {
            val handlers = transitionFinishHandlers ?: HashMap(schema.regions.size)
            handlers[targetRegionId] = ResolvedTransition.FinishHandler(
              flowPath = findParentFlowPathInclusive(schema, regionId, targetPathAbs),
              callback = target.onFinish as OnFinishHandler<Any, Any>
            )
            transitionFinishHandlers = handlers
          }
          is ScreenTarget -> Unit
        }
      }
      ResolvedTransition(
        targetPaths = targetPaths,
        finishHandlers = transitionFinishHandlers,
        payloads = payloads,
        enqueuedEvents = null,
      )
    }
    is Finish<*> -> {
      val flowPath = findParentFlowPathInclusive(schema, regionId, path)
      val parentFlowPath = if (flowPath.isRootInRegion(regionId)) flowPath else flowPath.dropLast(1)
      val handler = finishHandlers[flowPath] ?: error("no finish handler for \"$flowPath\"")
      val finishTransition = handler(transition.result)
      resolveTransitionInRegion(
        schema, regionId, finishTransition, parentFlowPath, activePath, nodes, nodeBuilder, finishHandlers,
        DoneEvent,
        extensionPoints,
      )
    }
    is Stay -> {
      ResolvedTransition(
        targetPaths = mapOf(regionId to path),
        finishHandlers = null,
        payloads = emptyMap(),
        enqueuedEvents = null,
      )
    }
    is Ignore -> {
      if (path.isRootInRegion(regionId)) {
        val resolved = maybeResolveBackEvent(
          schema, regionId, activePath, nodes, nodeBuilder, finishHandlers, event, extensionPoints
        )
        if (resolved == null) {
          println("no transition for event \"${event}\", ignoring")
          ResolvedTransition(
            targetPaths = mapOf(regionId to activePath),
            finishHandlers = null,
            payloads = emptyMap(),
            enqueuedEvents = null,
          )
        } else resolved
      } else {
        val parentPath = path.dropLast(1)
        val node = nodes[parentPath] ?: error("expected node to exist at path \"${parentPath}\"")
        resolveTransitionInRegion(
          schema, regionId, buildTransition(event, node, parentPath, extensionPoints),
          parentPath, activePath, nodes, nodeBuilder, finishHandlers, event, extensionPoints
        )
      }
    }
  }
}

private fun maybeResolveBackEvent(
  schema: Schema,
  regionId: RegionId,
  activePath: Path,
  nodes: Map<Path, Node>,
  nodeBuilder: NodeBuilder,
  finishHandlers: Map<Path, OnFinishHandler<Any, Any>>,
  event: Event,
  extensionPoints: List<NodeExtensionPoint>,
): ResolvedTransition? {
  if (event != Event.Back || activePath.segments.size <= 1) {
    return null
  }
  val newPath = activePath.dropLast(1)
  val transition = when (schema.nodeType(regionId, newPath)) {
    Schema.NodeType.Flow -> {
      val result = (nodes[newPath] as FlowNode<*>?)?.dismissResult
        ?: error("no flow node at path $newPath")
      Finish(result)
    }
    Schema.NodeType.Screen -> {
      NavigateTo(ScreenTarget(newPath))
    }
  }

  return resolveTransitionInRegion(
    schema,
    regionId,
    transition,
    activePath,
    activePath,
    nodes,
    nodeBuilder,
    finishHandlers,
    Event.Back,
    extensionPoints,
  )
}

private fun buildTransition(
  event: Event,
  node: Node,
  path: Path,
  extensionPoints: List<NodeExtensionPoint>,
): Transition {
  return if (event is InitEvent) {
    when (node) {
      is FlowNode<*> -> {
        NavigateTo(node.initial)
      }
      is ScreenNode -> {
        error("initial event is expected to be received on flow node only")
      }
    }
  } else {
    extensionPoints.forEach { it.onPreTransition(node, path, event) }
    when (node) {
      is FlowNode<*> -> {
        node.transition(event)
      }
      is ScreenNode -> {
        node.transition(event)
      }
    }.also { transition ->
      extensionPoints.forEach { it.onPostTransition(node, path, event, transition) }
    }
  }
}

private fun maybeResolveInitial(
  target: Target,
  targetPathAbs: Path,
  nodeBuilder: NodeBuilder,
  nodes: Map<Path, Node>,
  payloads: MutableMap<Path, Any>,
): Path {
  return when (target) {
    is ScreenTarget -> targetPathAbs
    is FlowTarget<*, *> -> {
      val flowNode = nodes.getOrElse(targetPathAbs) { nodeBuilder.build(targetPathAbs, payloads = payloads) }
      require(flowNode is FlowNode<*>) {
        "expected FlowNode at $targetPathAbs, but builder returned ${flowNode::class.simpleName}"
      }
      val nextTargetPathAbs = targetPathAbs.append(flowNode.initial.path)
      flowNode.initial.payload?.also { payloads[nextTargetPathAbs] = it }
      // TODO rework to be iterative, would be clearer in presence of mutable payloads parameter...
      maybeResolveInitial(flowNode.initial, nextTargetPathAbs, nodeBuilder, nodes, payloads)
    }
  }
}

private fun regionIdOfPath(regions: List<RegionId>, path: Path): RegionId? {
  // TODO implement this correctly, rather than always returning a first region!
  return regions.first()
}

/**
 * Returns a parent flow path of a [path]. If node at [path] is already a [FlowNode], returns the [path] unmodified
 */
private fun findParentFlowPathInclusive(path: Path, nodes: Map<Path, Node>): Path {
  return if (nodes[path] is FlowNode<*>) {
    path
  } else {
    path.dropLast(1)
  }
}

/**
 * Returns a parent flow path of a [path]. If node at [path] is already a [FlowNode], returns the [path] unmodified
 */
private fun findParentFlowPathInclusive(schema: Schema, regionId: RegionId, path: Path): Path {
  return if (schema.nodeType(regionId, path) == Schema.NodeType.Flow) {
    path
  } else {
    path.toStepsReversed().first { schema.nodeType(regionId, it) != Schema.NodeType.Screen }
  }
}

/**
 * Returns a parent flow path of a [path]. If [path] is has only one segment, returns null.
 */
private fun findParentFlowPath(schema: Schema, regionId: RegionId, path: Path): Path? {
  return if (path.segments.size == 1) {
    null
  } else {
    findParentFlowPathInclusive(schema, regionId, path.dropLast(1))
  }
}

private fun Path.isRootInRegion(regionId: RegionId): Boolean {
  return this == regionId.path
}

internal data class ResolvedTransition(
  val targetPaths: Map<RegionId, Path>,
  val finishHandlers: Map<RegionId, FinishHandler>?,
  val payloads: Map<Path, Any>,
  val enqueuedEvents: List<Event>?,
) {

  companion object {
    val EMPTY = ResolvedTransition(emptyMap(), null, emptyMap(), null)
  }

  data class FinishHandler(
    val flowPath: Path,
    val callback: OnFinishHandler<Any, Any>
  )
}
