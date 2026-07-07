package ru.kode.way

/**
 * Describes the static structure of a navigation graph.
 *
 * A [Schema] is generated at compile time from a `.dot` graph definition file by the `way`
 * Gradle plugin. It declares the regions, node types, and child schemas that make up one flow
 * or parallel node in the navigation hierarchy.
 *
 * Application code should not implement this interface directly; use the generated subclass.
 */
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

  fun createChildFlowFinishRequestEvent(regionId: RegionId, path: Path, result: Any): Event

  /**
   * Returns the [RegionId] of the sub-region whose final segment matches [name], or `null` when
   * no such region exists. The match is on the segment's name portion only — the `@<file>.dot`
   * disambiguator is stripped before comparison.
   *
   * Generated `*Schema` classes also expose typed getters per region (e.g.
   * `MyParallelSchema.exploreFlowRegionId`); prefer those at the call site. This helper exists
   * for dynamic lookups (test fixtures, debug tooling, multi-tenant code that picks regions by
   * name at runtime) and as a stable fallback for hand-rolled schemas.
   */
  fun regionByName(name: String): RegionId? = regions.firstOrNull {
    it.path.lastSegment().name == name
  }

  enum class NodeType {
    Flow,
    ParallelFlow,
    Screen,
  }
}
