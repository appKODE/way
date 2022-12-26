package ru.kode.way

class NavigationState internal constructor(
  internal val _regions: MutableMap<RegionId, Region>
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
}

class Region internal constructor(
  internal val _nodes: MutableMap<Path, Node>,
  internal var _active: Path,
  internal var _alive: MutableList<Path>,
) {
  val nodes: Map<Path, Node> = _nodes
  val active: Path get() = _active
  val activeNode get() = nodes[active] ?: error("internal error: no node at path $active")

  // TODO rename active -> attached/top/current, alive -> active?
  val alive: List<Path> get() = _alive

  override fun toString(): String {
    return "Region(_nodes=$_nodes, _active=$_active)"
  }
}
