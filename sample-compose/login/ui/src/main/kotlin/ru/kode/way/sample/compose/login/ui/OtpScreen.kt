package ru.kode.way.sample.compose.login.ui

import androidx.compose.runtime.Composable
import ru.kode.way.sample.compose.core.ui.SampleStubScreen
import ru.kode.way.sample.compose.login.ui.routing.LoginFlowEvent

@Composable
fun OtpScreen(sendEvent: (LoginFlowEvent) -> Unit) {
  SampleStubScreen(
    title = "Otp input",
    sendEvent = sendEvent,
    eventsClass = LoginFlowEvent::class,
    eventFilter = { it.simpleName?.contains("Otp") == true }
  )
}
