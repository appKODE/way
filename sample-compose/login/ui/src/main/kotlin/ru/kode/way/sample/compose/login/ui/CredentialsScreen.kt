package ru.kode.way.sample.compose.login.ui

import androidx.compose.runtime.Composable
import ru.kode.way.sample.compose.core.ui.SampleStubScreen
import ru.kode.way.sample.compose.login.ui.routing.LoginFlowEvent

@Composable
fun CredentialsScreen(sendEvent: (LoginFlowEvent) -> Unit) {
  SampleStubScreen(
    title = "Login credentials",
    sendEvent = sendEvent,
    eventsClass = LoginFlowEvent::class,
    eventFilter = { it.simpleName?.contains("Credentials") == true }
  )
}
