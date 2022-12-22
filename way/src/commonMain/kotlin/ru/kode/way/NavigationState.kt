package ru.kode.way

class NavigationState internal constructor(
  internal val _regions: MutableMap<Path, Region>
) {
  val regions: Map<Path, Region> = _regions

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as NavigationState

    if (_regions != other._regions) return false
    if (regions != other.regions) return false

    return true
  }

  override fun hashCode(): Int {
    return _regions.hashCode()
  }

  override fun toString(): String {
    return "NavigationState(_regions=$_regions)"
  }

}

class Region internal constructor(
  internal val _nodes: MutableMap<Path, Node>,
  internal var _active: Path
) {
  val nodes: Map<Path, Node> = _nodes
  val active: Path get() = _active
  val activeNode get() = nodes[active] ?: error("internal error: no node at path $active")

  override fun toString(): String {
    return "Region(_nodes=$_nodes, _active=$_active)"
  }
}
