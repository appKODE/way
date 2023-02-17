package ru.kode.way.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import ru.kode.way.FlowTransition
import ru.kode.way.NavigationService
import ru.kode.way.NavigationState
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.Schema

@Composable
fun NodeHost(service: NavigationService<*>) {
  LaunchedEffect(service) {
    service.start()
  }
  val activeNode by collectActiveNode(service)
  if (activeNode != null) {
    if (activeNode is ComposableNode) {
      (activeNode as ComposableNode).Content(Modifier)
    }
  }
}

@Composable
fun <R : Any> NodeHost(schema: Schema, nodeBuilder: NodeBuilder, onFinish: (R) -> FlowTransition<Unit>) {
  val service = remember(schema) { NavigationService(schema, nodeBuilder, onFinish) }
  NodeHost(service)
}

@Composable
private fun collectActiveNode(service: NavigationService<*>): State<Node?> {
  return produceState<Node?>(initialValue = null, service) {
    val listener = { s: NavigationState ->
      // TODO figure out how to render multiple regions
      println("alive nodes: ${s.regions.values.first().alive}")
      println("nodes: ${s.regions.values.first().nodes}")
      value = s.regions.values.first().activeNode
    }
    service.addTransitionListener(listener)
    awaitDispose {
      service.removeTransitionListener(listener)
    }
  }
}
