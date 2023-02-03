package ru.kode.way.sample.compose.permissions.ui.routing

import ru.kode.way.Event

sealed interface PermissionsFlowEvent : Event {
  object IntroDone : PermissionsFlowEvent
  object AllGranted : PermissionsFlowEvent
  object PartiallyGranted : PermissionsFlowEvent
}
