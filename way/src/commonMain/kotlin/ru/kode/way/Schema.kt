package ru.kode.way

interface Schema {
  companion object

  val regions: List<RegionId>

  fun children(regionId: RegionId): Set<Segment>
  fun children(regionId: RegionId, segment: Segment): Set<Segment>
  fun targets(regionId: RegionId): Map<Segment, Path>

  fun nodeType(regionId: RegionId, path: Path): NodeType

  enum class NodeType {
    Flow, Screen
  }
}
