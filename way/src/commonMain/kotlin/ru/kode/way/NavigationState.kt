package ru.kode.way

class NavigationState internal constructor(
  internal val _regions: MutableMap<RegionId, Region>,
  internal val _nodeExtensionPoints: MutableList<NodeExtensionPoint>,
  internal val _enqueuedEvents: ArrayDeque<Event>,
) {
  val regions: Map<RegionId, Region> = _regions

  override fun toString(): String {
    return "NavigationState(_regions=$_regions)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    other as NavigationState

    if (_regions != other._regions) return false

    return true
  }

  override fun hashCode(): Int {
    return _regions.hashCode()
  }

  // TODO @RemoveMutable remove if switch away from mutable collections happens
  internal fun copy(): NavigationState {
    return NavigationState(
      _regions = this.regions.mapValuesTo(mutableMapOf()) { it.value.copy() },
      _nodeExtensionPoints = this._nodeExtensionPoints.toMutableList(),
      _enqueuedEvents = ArrayDeque(this._enqueuedEvents)
    )
  }
}

class Region internal constructor(
  internal val _nodes: MutableMap<Path, Node>,
  internal var _active: Path,
  internal var _alive: MutableList<Path>,
  internal val _rootFinishTransitionBuilder: (Any) -> Transition,
) {
  val nodes: Map<Path, Node> = _nodes
  val active: Path get() = _active
  val activeNode get() = nodes[active] ?: error("internal error: no node at path $active")

  internal val rootTransitionBuilder = _rootFinishTransitionBuilder

  // TODO rename active -> attached/top/current, alive -> active?
  val alive: List<Path> get() = _alive

  // TODO @RemoveMutable remove if switch away from mutable collections happens
  internal fun copy(): Region {
    return Region(
      _nodes = this._nodes.toMutableMap(),
      _active = this._active,
      _alive = this._alive.toMutableList(),
      _rootFinishTransitionBuilder = this._rootFinishTransitionBuilder
    )
  }

  override fun toString(): String {
    return "Region(_nodes=$_nodes, _active=$_active)"
  }
}
