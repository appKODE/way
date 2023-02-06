package ru.kode.way.sample.compose.permissions.ui

import androidx.compose.runtime.Composable
import ru.kode.way.sample.compose.core.ui.SampleStubScreen
import ru.kode.way.sample.compose.permissions.ui.routing.PermissionsFlowEvent

@Composable
fun IntroScreen(sendEvent: (PermissionsFlowEvent) -> Unit) {
  SampleStubScreen(
    title = "Permissions Intro",
    sendEvent = sendEvent,
    eventsClass = PermissionsFlowEvent::class,
    eventFilter = { it == PermissionsFlowEvent.IntroDone::class }
  )
}
