package ru.kode.way

interface Schema {
  companion object

  val regions: List<RegionId>

  fun target(regionId: RegionId, segment: Segment): Path?

  fun nodeType(regionId: RegionId, path: Path): NodeType

  enum class NodeType {
    Flow, Parallel, Screen
  }
}
