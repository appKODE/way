package ru.kode.way.sample.compose.core.routing

import ru.kode.way.Event

fun interface FlowEventSink {
  fun sendEvent(event: Event)
}
