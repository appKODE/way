package ru.kode.way

interface Schema {
  companion object

  val regions: List<RegionId>

  val rootSegment: Segment
  val childSchemas: Map<SegmentId, Schema>

  /**
   * Builds a path for given [segmentId]. If this schema has child schemas they will be searched next when
   * the current schema has no segment with id equal to [segmentId].
   *
   * @param rootSegmentAlias when passed will be treated like current schema has root segment with this segment. Used
   * for schema composition, see [NodeBuilder.build] for more details on root segment aliases
   */
  fun target(regionId: RegionId, segmentId: SegmentId, rootSegmentAlias: Segment? = null): Path?

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
