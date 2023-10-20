package ru.kode.way.extension.service

import ru.kode.way.Event
import ru.kode.way.NavigationService
import ru.kode.way.NavigationState
import ru.kode.way.Path
import ru.kode.way.ServiceExtensionPoint

class LogTransitionsExtensionPoint<R : Any>(
  private val logAliveNodes: Boolean = false,
  private val logTargetResolveStartEvents: Boolean = true,
  private val logger: (msg: () -> String) -> Unit = { msg -> println(msg()) }
) : ServiceExtensionPoint<R> {
  private var preTransitionActivePath: Path? = null

  override fun onPreTransition(service: NavigationService<R>, event: Event, state: NavigationState) {
    preTransitionActivePath = state.regions.values.firstOrNull()?.active
    if (logTargetResolveStartEvents) {
      if (preTransitionActivePath != null) {
        logger { "$preTransitionActivePath ⨯ $event → [resolving target...]" }
      } else {
        logger { "$event → [resolving target...]" }
      }
    }
  }

  override fun onPostTransition(service: NavigationService<R>, event: Event, state: NavigationState) {
    if (preTransitionActivePath != null) {
      logger { "$preTransitionActivePath ⨯ $event → ${state.regions.values.first().active}" }
    } else {
      logger { "$event → ${state.regions.values.first().active}" }
    }
    if (logAliveNodes) {
      logger { "  alive nodes: ${state.regions.values.first().alive.joinToString()}" }
    }
  }
}
