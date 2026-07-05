package ru.kode.way.extension.service

import ru.kode.way.Event
import ru.kode.way.NavigationService
import ru.kode.way.NavigationState
import ru.kode.way.Path
import ru.kode.way.ServiceExtensionPoint

class LogTransitionsExtensionPoint<R : Any>(
  private val logAliveNodes: Boolean = false,
  private val logTargetResolveStartEvents: Boolean = true,
  private val logger: (msg: () -> String) -> Unit = { msg -> println(msg()) },
) : ServiceExtensionPoint<R> {
  private var preTransitionActivePaths: Map<String, Path> = emptyMap()

  override fun onPreTransition(service: NavigationService<R>, event: Event, state: NavigationState) {
    preTransitionActivePaths = state.activePathsByRegion()
    if (logTargetResolveStartEvents) {
      val activeDesc = preTransitionActivePaths.describe()
      if (activeDesc.isNotEmpty()) {
        logger { "$activeDesc ⨯ $event → [resolving target...]" }
      } else {
        logger { "$event → [resolving target...]" }
      }
    }
  }

  override fun onPostTransition(service: NavigationService<R>, event: Event, state: NavigationState) {
    val postDesc = state.activePathsByRegion().describe()
    val preDesc = preTransitionActivePaths.describe()
    if (preDesc.isNotEmpty()) {
      logger { "$preDesc ⨯ $event → $postDesc" }
    } else {
      logger { "$event → $postDesc" }
    }
    if (logAliveNodes) {
      state.regions.forEach { (regionId, region) ->
        logger { "  [${regionId.path}] alive: ${region.alive.joinToString()}" }
      }
    }
  }
}

/** The active path of every region keyed by the region's path string. */
private fun NavigationState.activePathsByRegion(): Map<String, Path> =
  regions.entries.associate { (regionId, region) -> regionId.path.toString() to region.active }

/** Renders `region:path` entries joined by ` | ` for a one-line transition log. */
private fun Map<String, Path>.describe(): String = entries.joinToString(" | ") { (name, path) -> "$name:$path" }
