package ru.kode.way

internal fun calculateAliveNodes(
  schema: Schema,
  state: NavigationState,
  targets: Map<RegionId, Path>
): NavigationState {
  targets.entries.forEach { (regionId, path) ->
    val region = state._regions[regionId] ?: error("no region with id=\"$regionId\"")
    val steps = path.toSteps()
    region._alive.removeAll { !steps.contains(it) }
    steps.forEach { if (!region._alive.contains(it)) region._alive.add(it) }
    region._active = region._alive.last()
  }
  return state
}
