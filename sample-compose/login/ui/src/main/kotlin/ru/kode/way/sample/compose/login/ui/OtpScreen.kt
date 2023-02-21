package ru.kode.way.sample.compose.login.ui

import androidx.compose.runtime.Composable
import ru.kode.way.sample.compose.core.ui.SampleStubScreen
import ru.kode.way.sample.compose.login.ui.routing.LoginFlowEvent

@Composable
fun OtpScreen(maskInput: Boolean?, sendEvent: (LoginFlowEvent) -> Unit) {
  SampleStubScreen(
    title = "Otp input (maskInput=$maskInput)",
    sendEvent = sendEvent,
    eventsClass = LoginFlowEvent::class,
    eventFilter = { it.simpleName?.contains("Otp") == true }
  )
}
