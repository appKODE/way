package ru.kode.way

internal fun resolveTransition(
  schema: Schema,
  regions: Map<RegionId, Region>,
  nodeBuilder: NodeBuilder,
  event: Event
): ResolvedTransition {
  return regions.entries.fold(ResolvedTransition.EMPTY) { acc, (regionId, region) ->
    val resolved = resolveTransitionInRegion(
      schema = schema,
      regionId = regionId,
      path = region.active,
      activePath = region.active,
      nodes = region.nodes,
      nodeBuilder = nodeBuilder,
      event = event
    )
    ResolvedTransition(
      targetPaths = acc.targetPaths + resolved.targetPaths,
      finishHandler = (acc.finishHandler.orEmpty() + resolved.finishHandler.orEmpty()).takeIf { it.isNotEmpty() },
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
  path: Path,
  activePath: Path,
  nodes: Map<Path, Node>,
  nodeBuilder: NodeBuilder,
  event: Event
): ResolvedTransition {
  val node = nodes[path] ?: error("expected node to exist at path \"$path\"")
  return when (val transition = buildTransition(event, node)) {
    is EnqueueEvent -> ResolvedTransition(
      targetPaths = mapOf(regionId to path),
      finishHandler = null,
      enqueuedEvents = listOf(transition.event),
    )
    is NavigateTo -> {
      val finishHandlers = HashMap<Path, OnFinishHandler<Any, Any>>(transition.targets.size)
      val targetPaths = HashMap<RegionId, Path>(transition.targets.size)
      transition.targets.forEach { target ->
        // there maybe several targets in different regions
        val targetRegionId = regionIdOfPath(schema.regions, target.path)
          ?: error("failed to find regionId for path=\"${target.path}\"")
        val targetPathAbs = schema.targets(targetRegionId)[target.path.segments.last()]
          ?: error("failed to find schema entry for target \"${target.path}\"")
        targetPaths[targetRegionId] = maybeResolveInitial(target, targetPathAbs, nodeBuilder, nodes)
        when (target) {
          is FlowTarget<*, *> -> {
            finishHandlers[targetPathAbs] = target.onFinish as OnFinishHandler<Any, Any>
          }
          is ScreenTarget -> Unit
        }
      }
      ResolvedTransition(
        targetPaths = targetPaths,
        finishHandler = finishHandlers.takeIf { it.isNotEmpty() },
        enqueuedEvents = null,
      )
    }
    is Finish<*> -> TODO()
    is Stay -> {
      ResolvedTransition(
        targetPaths = mapOf(regionId to path),
        finishHandler = null,
        enqueuedEvents = null,
      )
    }
    is Ignore -> {
      println("no transition for event \"${event}\" on $path, ignoring")
      if (path.isRootInRegion(regionId)) {
        ResolvedTransition(
          targetPaths = mapOf(regionId to activePath),
          finishHandler = null,
          enqueuedEvents = null,
        )
      } else {
        val parentPath = path.dropLast(1)
        resolveTransitionInRegion(schema, regionId, parentPath, activePath, nodes, nodeBuilder, event)
      }
    }
  }
}

private fun buildTransition(
  event: Event,
  node: Node
): Transition {
  return if (event == Event.Init) {
    when (node) {
      is FlowNode<*, *> -> {
        NavigateTo(node.initial)
      }
      is ScreenNode<*> -> {
        error("initial event is expected to be received on flow node only")
      }
    }
  } else {
    when (node) {
      is FlowNode<*, *> -> {
        (node as FlowNode<Event, *>).transition(event)
      }
      is ScreenNode<*> -> {
        (node as ScreenNode<Event>).transition(event)
      }
    }
  }
}

private fun maybeResolveInitial(
  target: Target,
  targetPathAbs: Path,
  nodeBuilder: NodeBuilder,
  nodes: Map<Path, Node>
): Path {
  return when (target) {
    is ScreenTarget -> targetPathAbs
    is FlowTarget<*, *> -> {
      val flowNode = nodes.getOrElse(targetPathAbs) { nodeBuilder.build(targetPathAbs) }
      require(flowNode is FlowNode<*, *>) {
        "expected FlowNode at $targetPathAbs, but builder returned ${flowNode::class.simpleName}"
      }
      maybeResolveInitial(flowNode.initial, targetPathAbs.append(flowNode.initial.path), nodeBuilder, nodes)
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
  return if (nodes[path] is FlowNode<*, *>) {
    path
  } else {
    path.dropLast(1)
  }
}

private fun Path.isRootInRegion(regionId: RegionId): Boolean {
  return this == regionId.path
}

internal data class ResolvedTransition(
  val targetPaths: Map<RegionId, Path>,
  val finishHandler: Map<Path, OnFinishHandler<Any, Any>>?,
  val enqueuedEvents: List<Event>?,
) {
  companion object {
    val EMPTY = ResolvedTransition(emptyMap(), null, null)
  }
}
