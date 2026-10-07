package ru.kode.way

import kotlin.jvm.JvmInline

@JvmInline
value class RegionId(val path: Path) {
  override fun toString(): String = path.toString()
}

/**
 * Resolves a schema-relative [RegionId] (e.g. a generated `Schema.exploreFlowRegionId`, which
 * carries only the path relative to its own schema) to the absolute [RegionId] used as the key
 * in [NavigationState.regions], given the absolute path of the enclosing parallel node.
 *
 * Already-absolute region ids (their path already starts with [parentPath]) are returned
 * unchanged. Mirrors the runtime's own `absoluteRegionRoot` resolution (see
 * `TargetResolution.kt`) and the identical inline logic `NodeHost(regionId = ...)` uses — this is
 * that same resolution, extracted so app code reading a region's state outside of `NodeHost`
 * (e.g. via [ru.kode.way.compose.collectActiveNode]) doesn't have to re-derive it by hand.
 *
 * [parentPath] is `null` when there is no enclosing parallel (the schema root is not a
 * [ParallelFlowNode]) — [regionId] is returned unchanged in that case, since there is nothing to
 * resolve against.
 */
fun RegionId.resolveAbsolute(parentPath: Path?): RegionId {
  if (parentPath == null || path.startsWith(parentPath)) return this
  // The id is relative to the schema which declares the region, and [parentPath] ends with the part of the id which
  // leads from that schema to the parallel node: nothing for the root of the schema, more for a parallel node inside
  // of it. The first segment is not compared, a schema is mounted under the name its parent gives it.
  val segments = path.segments
  val parentSegments = parentPath.segments
  val overlap = (segments.size - 2 downTo 1).firstOrNull { count ->
    count <= parentSegments.size && parentSegments.takeLast(count) == segments.subList(1, 1 + count)
  } ?: 0
  val tail = segments.drop(1 + overlap)
  return RegionId(if (tail.isEmpty()) parentPath else parentPath.append(Path(tail)))
}
