package ru.kode.way.sample.compose.login.ui.routing

import ru.kode.way.Event

sealed interface LoginFlowEvent : Event {
  object CredentialsReady : LoginFlowEvent
  object OtpError : LoginFlowEvent
  object OtpSuccess : LoginFlowEvent
}
