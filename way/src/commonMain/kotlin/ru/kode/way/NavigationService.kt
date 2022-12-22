package ru.kode.way

// TODO add constructor for parallel regions: it would accept several roots
class NavigationService(private val schema: Schema, private val nodeBuilder: NodeBuilder) {
  private var state: NavigationState = NavigationState(_regions = mutableMapOf())
  private val listeners = ArrayList<(NavigationState) -> Unit>()

  fun start() {
    sendEvent(Event.Init)
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

  private fun transition(state: NavigationState, event: Event): NavigationState {
    check(event == Event.Init || !state.isInitialized()) {
      "internal error: no regions in state after Event.Init"
    }
    if (event == Event.Init) {
      schema.regions.forEach { regionPath ->
        val nodes = nodeBuilder.build(regionPath)
        require(nodes.size == 1) { "expected 1 node as the root of region $regionPath, but builder returned several nodes: $nodes"}
        val regionRoot = nodes.values.first()
        require(regionRoot is FlowNode<*, *>) { "expected FlowNode at $regionPath, but builder returned ${regionRoot::class.simpleName}"}
        val initialNodes = resolveInitialNode(regionPath, initialTarget = regionRoot.initial)
        state._regions[regionPath] = Region(
          _nodes = initialNodes.toMutableMap(),
          _active = initialNodes.keys.last(),
        )
      }
    } else {

    }
    return state
  }

  private fun resolveInitialNode(currentNodePath: Path, initialTarget: Target): Map<Path, Node> {
    val initialTargetAbs = Path(currentNodePath.segments + initialTarget.path.segments)
    return when (initialTarget) {
      is FlowTarget<*, *> -> {
        val initialNodes = nodeBuilder.build(initialTargetAbs)
        val flowNode = initialNodes.values.lastOrNull()
          ?: error("no nodes were built for initial target at \"${initialTargetAbs}\"")
        require(flowNode is FlowNode<*, *>) { "expected FlowNode at ${initialTargetAbs}, but builder returned ${flowNode::class.simpleName}"}
        initialNodes + resolveInitialNode(
          initialTargetAbs,
          flowNode.initial
        )
      }
      is ScreenTarget -> {
        val initialNodes = nodeBuilder.build(initialTargetAbs)
        initialNodes
      }
    }
  }

  fun sendEvent(event: Event) {
    state = transition(state, event)
    listeners.forEach { it(state) }
  }
}


private fun NavigationState.isInitialized(): Boolean {
  return this.regions.isNotEmpty()
}

// TODO be more sensible!
private fun Schema.regionCount() = 1
