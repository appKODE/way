package ru.kode.way

interface Schema {
  companion object

  val regions: List<RegionId>

  val rootSegment: Segment
  val childSchemas: Map<Segment, Schema>

  /**
   * Builds a path for a given  [segment].
   *
   * @param rootSegmentAlias when passed will be treated like current schema has root segment with this segment. Used
   * for schema composition, see [NodeBuilder.build] for more details on root segment aliases
   */
  fun target(regionId: RegionId, segment: Segment, rootSegmentAlias: Segment? = null): Path?

  /**
   * Returns a node type for [path]. If this schema has child schemas they will be searched next when
   * the current schema has no nodes with relative path equal to [path]
   *
   * @param rootSegmentAlias when passed will be treated like current schema has root segment with this id. Used
   * for schema composition, see [NodeBuilder.build] for more details on root segment aliases
   */
  fun nodeType(regionId: RegionId, path: Path, rootSegmentAlias: Segment? = null): NodeType

  enum class NodeType {
    Flow, Parallel, Screen
  }
}
