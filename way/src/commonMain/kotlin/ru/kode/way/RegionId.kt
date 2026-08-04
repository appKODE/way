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
  val tail = if (path.length > 1) path.drop(1) else null
  return RegionId(if (tail != null) parentPath.append(tail) else parentPath)
}
