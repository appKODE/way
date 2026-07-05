package ru.kode.way

/**
 * Resolves [regionId] to one of the [candidates] (the alive sub-regions of a parallel node).
 * Three tiers, narrowest first:
 *
 * 1. **Strict equality** — `regionId in candidates`. The common case when the caller already
 *    supplies an absolute id pulled out of `NavigationState.regions.keys`.
 * 2. **Strict full-id suffix** — every segment of `regionId.path` equals the trailing segments of
 *    the candidate by full [Segment.id]. Covers bare-segment test fixtures and any in-module
 *    schema-local constant where the boundary `@graphId:file` disambiguator happens to agree.
 * 3. **Boundary-tolerant suffix** — only the first segment of `regionId.path` (the schema-root
 *    mount point) is matched by [Segment.name]; every deeper segment still requires full id
 *    equality. Covers cross-module schema imports where the parent module's codegen stamps its own
 *    `@graphId:file` on the boundary segment while the leaf module's codegen stamps the leaf's. The
 *    relaxation is safe because boundary segment names are unique within one parallel parent's
 *    `childSchemas` and deeper segments still have to match strictly.
 *
 * Returns `null` if no tier matches — Back soft-falls-back to [deepestRegion]; NavigateTo-from-a-
 * parallel treats it as an error.
 */
internal fun resolveRegionId(regionId: RegionId, candidates: Collection<RegionId>): RegionId? {
  if (regionId in candidates) return regionId
  candidates.firstOrNull { it.path.endsWith(regionId.path) }?.let { return it }
  return candidates.firstOrNull { it.path.endsWithSchemaLocal(regionId.path) }
}

/**
 * Selects the sub-region that Back should target when a parallel node declines to name one (returns
 * [Ignore] or a stale/unresolved [DispatchBackTo]): the region with the longest active path.
 *
 * When several regions share that maximal depth, the tiebreaker is the [RegionId.path] string — a
 * stable, KMP-safe alphabetical fallback. Never throws: a parallel always has at least one active
 * sub-region when Back reaches it.
 */
internal fun deepestRegion(subRegionActivePaths: Map<RegionId, Path>): RegionId {
  val maxLen = subRegionActivePaths.values.maxOf { it.length }
  // maxByOrNull already returns the sole element for a single deepest region, so no special-case needed.
  return subRegionActivePaths
    .filterValues { it.length == maxLen }.keys
    .maxByOrNull { it.path.toString() }!!
}

/**
 * Picks the sub-region a Back should enter: the [requested] id normalized against the alive
 * [subRegions] via [resolveRegionId], soft-falling-back to the [deepestRegion] when [requested] is
 * null or stale/unresolved. Back never throws.
 */
internal fun chooseBackRegion(requested: RegionId?, subRegions: Map<RegionId, Path>): RegionId =
  resolveRegionId(requested ?: return deepestRegion(subRegions), subRegions.keys) ?: deepestRegion(subRegions)
