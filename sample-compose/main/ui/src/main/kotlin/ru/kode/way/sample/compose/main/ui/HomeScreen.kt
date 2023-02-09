package ru.kode.way.sample.compose.main.ui

import androidx.compose.runtime.Composable
import ru.kode.way.sample.compose.core.ui.SampleStubScreen
import ru.kode.way.sample.compose.main.ui.routing.MainFlowEvent

@Composable
fun HomeScreen(sendEvent: (MainFlowEvent) -> Unit) {
  SampleStubScreen(
    title = "Main Home",
    sendEvent = sendEvent,
    eventsClass = MainFlowEvent::class,
  )
}
