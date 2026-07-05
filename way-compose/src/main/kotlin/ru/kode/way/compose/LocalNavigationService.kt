package ru.kode.way.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
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

@Composable
fun NavigationService<*>.collectAsState(): State<NavigationState?> =
  produceTransitionState<NavigationState?>(initial = null) { it }
