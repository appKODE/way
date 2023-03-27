package ru.kode.way.sample.compose.main.parallel.ui.routing

import ru.kode.way.Event

sealed interface MainParallelFlowEvent : Event {
  object EditProfileRequested : MainParallelFlowEvent
}
