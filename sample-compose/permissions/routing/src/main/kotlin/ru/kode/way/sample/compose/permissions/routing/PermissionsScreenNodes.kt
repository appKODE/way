package ru.kode.way.sample.compose.permissions.routing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.kode.way.Event
import ru.kode.way.Ignore
import ru.kode.way.ScreenNode
import ru.kode.way.ScreenTransition
import ru.kode.way.compose.ComposableNode
import ru.kode.way.sample.compose.permissions.ui.IntroScreen
import ru.kode.way.sample.compose.permissions.ui.RequestScreen
import ru.kode.way.sample.compose.permissions.ui.routing.PermissionsFlowEvent
import javax.inject.Inject

fun interface FlowEventSink {
  fun sendEvent(event: Event)
}

class IntroNode @Inject constructor(
  private val eventSink: FlowEventSink
) : ScreenNode<PermissionsFlowEvent>, ComposableNode {
  override fun transition(event: PermissionsFlowEvent): ScreenTransition {
    return Ignore
  }

  @Composable
  override fun Content(modifier: Modifier) {
    // viewModel could be injected with dagger into this screen node class and passed as
    // an argument to screen function
    IntroScreen(eventSink::sendEvent)
  }
}

class RequestNode @Inject constructor(
  private val eventSink: FlowEventSink
) : ScreenNode<PermissionsFlowEvent>, ComposableNode {
  override fun transition(event: PermissionsFlowEvent): ScreenTransition {
    return Ignore
  }

  @Composable
  override fun Content(modifier: Modifier) {
    // viewModel could be injected with dagger into this screen node class and passed as
    // an argument to screen function
    RequestScreen(eventSink::sendEvent)
  }
}
