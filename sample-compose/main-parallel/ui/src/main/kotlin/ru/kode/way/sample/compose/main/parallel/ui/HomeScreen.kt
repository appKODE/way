package ru.kode.way.sample.compose.main.parallel.ui

import androidx.compose.runtime.Composable
import ru.kode.way.sample.compose.core.ui.SampleStubScreen
import ru.kode.way.sample.compose.main.parallel.ui.routing.MainParallelFlowEvent

@Composable
fun HomeScreen(sendEvent: (MainParallelFlowEvent) -> Unit) {
  SampleStubScreen(
    title = "Main Home",
    sendEvent = sendEvent,
    eventsClass = MainParallelFlowEvent::class,
  )
}
