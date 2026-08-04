package ru.kode.way

/**
 * Updates [state] in-place by recomputing alive/active paths for each region based on [targets].
 * IMPORTANT: This function mutates [state._regions] directly. Callers that need the pre-mutation
 * state must snapshot it before calling this function (see [NavigationService.transition]).
 * Returns the same [state] object for convenience.
 */
internal fun calculateAliveNodes(
  state: NavigationState,
  targets: Map<RegionId, Path>,
  schema: Schema,
): NavigationState {
  targets.entries.forEach { (regionId, path) ->
    val steps = path.toSteps().filter { it.startsWith(regionId.path) }.toList()
    // A target path must pass through its region's root; otherwise `steps` is empty, `_alive`
    // would be emptied below and `_active = _alive.last()` would throw an opaque
    // NoSuchElementException. Fail with an actionable message instead — this signals the target
    // was routed to a region whose root is not a prefix of the target (an upstream resolution bug).
    check(steps.isNotEmpty()) {
      "calculateAliveNodes: target path \"$path\" does not pass through region root \"${regionId.path}\"; " +
        "cannot compute alive nodes. The target was routed to the wrong region."
    }
    val region = state._regions.getOrPut(regionId) {
      Region(
        _nodes = mutableMapOf(),
        _active = path,
        _alive = mutableListOf(),
        _rootFinishTransitionBuilder = computeSubRegionFinishBuilder(schema, regionId),
      )
    }

    val stepsSet = steps.toHashSet()
    val aliveSet = region._alive.toHashSet()
    region._alive.removeAll { it !in stepsSet }
    steps.forEach { if (it !in aliveSet) region._alive.add(it) }
    region._active = region._alive.last()
    // Set once, on this region's first-ever resolution (whether that's InitEvent's
    // FlowNode.initial chain or a lazily-mounted region's first NavigateTo) — never touched again
    // on subsequent navigation. See Region.rootPath.
    if (region._rootPath == null) region._rootPath = region._active
  }

  pruneOrphanRegions(state, schema)
  return state
}

/**
 * The SCXML "configuration": the set of every currently-active absolute [Path] across all regions —
 * the union of each region's `alive` list. This is the explicit state set the canonical statechart
 * functions in StatechartAlgorithm.kt (e.g. [computeExitSet]) operate over. Centralizing it here
 * gives the runtime one named abstraction instead of ad-hoc `flatMap { it.alive }` reconstructions.
 */
internal fun computeConfiguration(state: NavigationState): Set<Path> =
  state._regions.values.flatMapTo(mutableSetOf()) { it.alive }

/**
 * Removes from [state]._regions every sub-region whose parent parallel is no longer reachable, keeping:
 * - schema-declared top-level regions (always retained), and
 * - sub-regions whose parent parallel is still alive — either (a) a runtime node alive in a sibling
 *   region (sub-regions lazily mounted by NavigateTo) or (b) an intermediate parallel root
 *   (parallel-rooted schema mounted as a sub-region of another parallel-rooted schema) that lives in
 *   [NavigationState._intermediateParallels] rather than any region. Without the (b) check,
 *   intermediate-parallel sub-regions would be dropped on the first pass because their parent isn't in
 *   any `alive` list.
 *
 * Shared by [calculateAliveNodes] and the post-update intermediate-unmount sweep in
 * [NavigationService.transition] so the two callers cannot drift.
 */
internal fun pruneOrphanRegions(state: NavigationState, schema: Schema) {
  state._regions.keys.retainAll { regionId ->
    if (schema.regions.contains(regionId)) return@retainAll true
    val parallelParentPath = regionId.path.dropLast(1)
    if (parallelParentPath in state._intermediateParallels.keys) return@retainAll true
    val parentAliveInSibling = state._regions.entries.any { (otherId, otherRegion) ->
      otherId != regionId && parallelParentPath in otherRegion.alive
    }
    parentAliveInSibling
  }
}
