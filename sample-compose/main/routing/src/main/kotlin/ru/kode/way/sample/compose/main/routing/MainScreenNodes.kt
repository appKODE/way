// ktlint-disable filename
package ru.kode.way.sample.compose.main.routing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.kode.way.Event
import ru.kode.way.Ignore
import ru.kode.way.ScreenNode
import ru.kode.way.ScreenTransition
import ru.kode.way.compose.ComposableNode
import ru.kode.way.sample.compose.core.routing.FlowEventSink
import ru.kode.way.sample.compose.main.ui.HomeScreen
import javax.inject.Inject

class HomeNode @Inject constructor(
  private val eventSink: FlowEventSink
) : ScreenNode, ComposableNode {
  override fun transition(event: Event): ScreenTransition {
    return Ignore
  }

  @Composable
  override fun Content(modifier: Modifier) {
    // viewModel could be injected with dagger into this screen node class and passed as
    // an argument to screen function
    HomeScreen(eventSink::sendEvent)
  }
}
