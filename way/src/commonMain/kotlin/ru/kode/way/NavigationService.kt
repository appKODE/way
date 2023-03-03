package ru.kode.way

class NavigationService<R : Any>(
  private val schema: Schema,
  private val nodeBuilder: NodeBuilder,
  private val onFinish: (R) -> FlowTransition<Unit>
) {
  private var state: NavigationState = NavigationState(
    _regions = mutableMapOf(),
    _nodeExtensionPoints = mutableListOf()
  )
  private val listeners = ArrayList<(NavigationState) -> Unit>()

  fun start(rootFlowPayload: Any? = null) {
    sendEvent(InitEvent(rootFlowPayload))
  }

  fun addTransitionListener(listener: (NavigationState) -> Unit) {
    listeners.add(listener)
    if (state.isInitialized()) {
      listener(state)
    }
  }

  fun removeTransitionListener(listener: (NavigationState) -> Unit) {
    listeners.remove(listener)
  }

  fun addNodeExtensionPoint(point: NodeExtensionPoint) {
    state._nodeExtensionPoints.add(point)
  }

  fun removeNodeExtensionPoint(point: NodeExtensionPoint) {
    state._nodeExtensionPoints.remove(point)
  }

  private fun transition(state: NavigationState, event: Event): NavigationState {
    check(event is InitEvent || state.isInitialized()) {
      "internal error: no regions in state after Event.Init"
    }
    if (event is InitEvent) {
      schema.regions.forEach { regionId ->
        val regionRootPath = regionId.path
        val regionRoot = nodeBuilder.build(
          regionRootPath,
          payloads = event.payload?.let { mapOf(regionRootPath to it) } ?: emptyMap()
        )
        require(regionRoot is FlowNode<*>) {
          "expected FlowNode at $regionId, but builder returned ${regionRoot::class.simpleName}"
        }
        callOnEntry(regionRoot, regionRootPath, state._nodeExtensionPoints)
        state._regions[regionId] = Region(
          _nodes = mutableMapOf(regionRootPath to regionRoot),
          _active = regionRootPath,
          _alive = mutableListOf(regionRootPath),
          _finishHandlers = mutableMapOf(
            regionRootPath to (onFinish as (Any) -> FlowTransition<Any>)
          ),
        )
      }
    }
    println("on [$event] transition")
    val resolvedTransition = resolveTransition(schema, state.regions, nodeBuilder, event, state._nodeExtensionPoints)
    println("=> resolved to ${resolvedTransition.targetPaths.values.first()}")
    val previousAlive = state._regions.mapValues { it.value.alive.toList() }
    return calculateAliveNodes(schema, state, resolvedTransition.targetPaths).also {
      storeFinishHandlers(it, resolvedTransition)
      synchronizeNodes(it, resolvedTransition.payloads, previousAlive)
      // TODO remove after codegen impl, or run only in debug / during tests?
      checkSchemaValidity(schema, it)
    }
  }

  private fun checkSchemaValidity(schema: Schema, state: NavigationState) {
    state.regions.forEach { (regionId, region) ->
      region._nodes.forEach { (path, node) ->
        val nodeType = schema.nodeType(regionId, path)
        when (node) {
          is FlowNode<*> -> {
            check(nodeType == Schema.NodeType.Flow) {
              "according to schema, \"$path\" should be a $nodeType, but it is a ${FlowNode::class.simpleName}"
            }
          }
          is ScreenNode -> {
            check(nodeType == Schema.NodeType.Screen) {
              "according to schema, \"$path\" should be a $nodeType, but it is a ${ScreenNode::class.simpleName}"
            }
          }
        }
      }
    }
  }

  private fun storeFinishHandlers(state: NavigationState, resolvedTransition: ResolvedTransition) {
    if (resolvedTransition.finishHandlers == null) return
    resolvedTransition.finishHandlers.forEach { (regionId, handler) ->
      val region = state._regions[regionId] ?: error("no region with id \"$regionId\"")
      // the path to the flow finish handler of which we are storing
      region._finishHandlers[handler.flowPath] = handler.callback
    }
  }

  private fun synchronizeNodes(
    state: NavigationState,
    payloads: Map<Path, Any>,
    previousAlive: Map<RegionId, List<Path>>
  ) {
    state._regions.forEach { (regionId, region) ->
      previousAlive[regionId].orEmpty().reversed().forEach { path ->
        if (!region.alive.contains(path)) {
          val node = region._nodes[path] ?: error("state doesn't contain node at \"$path\"")
          callOnExit(node, path, state._nodeExtensionPoints)
        }
      }
      region.alive.forEach { path ->
        region._nodes.keys.retainAll(region.alive.toSet())
        if (!region._nodes.containsKey(path)) {
          region._nodes[path] = nodeBuilder.build(path, payloads)
            .also {
              callOnEntry(it, path, state._nodeExtensionPoints)
            }
        }
      }
      region._finishHandlers.keys.retainAll(region.alive.toSet())
    }
  }

  fun sendEvent(event: Event) {
    state = transition(state, event)
    val validityErrors = state.runValidityChecks()
    if (validityErrors.isNotEmpty()) {
      error(validityErrors.joinToString("\n", prefix = "internal error. State is inconsistent:\n"))
    }
    listeners.forEach { it(state) }
  }
}

private fun NavigationState.runValidityChecks(): List<String> {
  return regions.mapNotNull { (regionId, region) ->
    if (region.alive.toSet() != region.nodes.keys) {
      "region \"$regionId\": alive node path set is different from nodes set. Alive paths: " +
        "${region.alive}, alive nodes: ${region.nodes.keys}"
    } else null
  }
}

private fun NavigationState.isInitialized(): Boolean {
  return this.regions.isNotEmpty()
}

// TODO be more sensible, actually calculate!
private fun Schema.regionCount() = 1

private fun callOnEntry(node: Node, path: Path, extensionPoints: List<NodeExtensionPoint>) {
  extensionPoints.forEach { it.onPreEntry(node, path) }
  node.onEntry()
  extensionPoints.forEach { it.onPostEntry(node, path) }
}

private fun callOnExit(node: Node, path: Path, extensionPoints: List<NodeExtensionPoint>) {
  extensionPoints.forEach { it.onPreExit(node, path) }
  node.onExit()
  extensionPoints.forEach { it.onPostExit(node, path) }
}
