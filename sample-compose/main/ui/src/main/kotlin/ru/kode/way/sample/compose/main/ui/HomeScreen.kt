package ru.kode.way.sample.compose.main.ui

import androidx.compose.runtime.Composable
import ru.kode.way.sample.compose.core.ui.SampleStubScreen
import ru.kode.way.sample.compose.main.ui.routing.MainFlowEvent

@Composable
fun HomeScreen(send: (MainFlowEvent) -> Unit) {
  SampleStubScreen(
    title = "Main Home",
    send = send,
    eventsClass = MainFlowEvent::class,
  )
}
