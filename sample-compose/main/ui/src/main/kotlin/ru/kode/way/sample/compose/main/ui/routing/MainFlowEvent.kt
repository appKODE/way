package ru.kode.way.sample.compose.main.ui.routing

import ru.kode.way.Event

sealed interface MainFlowEvent : Event {
  object EditProfileRequested : MainFlowEvent
}
