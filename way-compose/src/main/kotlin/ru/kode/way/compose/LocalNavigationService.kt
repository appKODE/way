package ru.kode.way.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.compositionLocalWithComputedDefaultOf
import ru.kode.way.EventSink
import ru.kode.way.NavigationService
import ru.kode.way.NavigationState
import ru.kode.way.Path

val LocalNavigationService: ProvidableCompositionLocal<NavigationService<*>> =
  compositionLocalOf {
    error(
      "no NavigationService provided — wrap content with LocalNavigationService.provides(service)",
    )
  }

/**
 * Provides the absolute [Path] of the node currently being rendered by the enclosing [NodeHost].
 * Read this inside a parallel node's [ComposableNode.Content] body when you need to compute absolute
 * sub-region RegionIds from schema-relative ones declared in the parallel's own schema.
 *
 * Null when no [NodeHost] is in the composition above.
 */
val LocalNodePath: ProvidableCompositionLocal<Path?> = compositionLocalOf { null }

/**
 * The nearest [EventSink]: send every UI event through it. Inside a node's [ComposableNode.Content] rendered by
 * [NodeHost] it is that node's [NavigationService.eventSink]: events are resolved from this screen (or from the active
 * leaves under this flow or parallel), bubbling up through the node to its ancestors, reaching every parallel
 * enclosing the node and bubbling up through each parallel's ancestors, and dropped once the node has left navigation
 * (e.g. a tap on a screen which is still animating out). Outside any node, but under [LocalNavigationService], it is
 * the service itself, the root sink (whole tree, never stale).
 */
val LocalEventSink: ProvidableCompositionLocal<EventSink> =
  compositionLocalWithComputedDefaultOf { LocalNavigationService.currentValue }

@Composable
fun NavigationService<*>.collectAsState(): State<NavigationState?> =
  produceTransitionState<NavigationState?>(initial = null) { it }
