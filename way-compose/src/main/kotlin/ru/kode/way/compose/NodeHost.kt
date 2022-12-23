package ru.kode.way.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import ru.kode.way.NavigationService
import ru.kode.way.NavigationState
import ru.kode.way.NodeBuilder
import ru.kode.way.Schema

@Composable
fun NodeHost(schema: Schema, nodeBuilder: NodeBuilder) {
  val service = remember(schema) {
    NavigationService(schema, nodeBuilder)
  }
  LaunchedEffect(schema) {
    service.start()
  }
  val navigationState by collectNavigationState(service)
  if (navigationState != null) {
    // TODO figure out how to render multiple regions
    val activeNode = navigationState!!.regions.values.first().activeNode
    if (activeNode is ComposableNode) {
      activeNode.Content(Modifier)
    }
  }
}

@Composable
private fun collectNavigationState(service: NavigationService): State<NavigationState?> {
  return produceState<NavigationState?>(initialValue = null, service) {
    val listener = { s: NavigationState -> value = s }
    service.addTransitionListener(listener)
    awaitDispose {
      service.removeTransitionListener(listener)
    }
  }
}
