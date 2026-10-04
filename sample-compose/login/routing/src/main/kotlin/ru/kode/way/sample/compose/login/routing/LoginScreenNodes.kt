package ru.kode.way.sample.compose.login.routing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.kode.way.Event
import ru.kode.way.Ignore
import ru.kode.way.ScreenNode
import ru.kode.way.ScreenTransition
import ru.kode.way.compose.ComposableNode
import ru.kode.way.compose.LocalEventSink
import ru.kode.way.sample.compose.login.ui.CredentialsScreen
import ru.kode.way.sample.compose.login.ui.OtpScreen
import javax.inject.Inject

class CredentialsNode @Inject constructor() :
  ScreenNode,
  ComposableNode {
  override fun transition(event: Event): ScreenTransition = Ignore

  @Composable
  override fun Content(modifier: Modifier) {
    // viewModel could be injected with dagger into this screen node class and passed as
    // an argument to screen function
    CredentialsScreen(LocalEventSink.current::send)
  }
}

class OtpNode @Inject constructor() :
  ScreenNode,
  ComposableNode {

  var maskInput: Boolean? = null

  override fun transition(event: Event): ScreenTransition = Ignore

  @Composable
  override fun Content(modifier: Modifier) {
    // viewModel could be injected with dagger into this screen node class and passed as
    // an argument to screen function
    OtpScreen(maskInput, LocalEventSink.current::send)
  }
}
