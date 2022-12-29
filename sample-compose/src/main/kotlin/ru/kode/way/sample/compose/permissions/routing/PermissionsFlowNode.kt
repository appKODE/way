package ru.kode.way.sample.compose.permissions.routing

import ru.kode.way.Event
import ru.kode.way.Finish
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.NavigateTo
import ru.kode.way.sample.compose.core.routing.FlowResult

sealed interface PermissionsEvent : Event {
  object IntroDone : PermissionsEvent
  object AllGranted : PermissionsEvent
}

class PermissionsFlowNode : FlowNode<PermissionsEvent, FlowResult> {
  override val initial = PermissionsPaths.intro

  override fun transition(event: PermissionsEvent): FlowTransition<FlowResult> {
    return when (event) {
      PermissionsEvent.IntroDone -> NavigateTo(PermissionsPaths.request)
      PermissionsEvent.AllGranted -> Finish(FlowResult.Dismissed)
    }
  }

  override val dismissResult: FlowResult = FlowResult.Dismissed

  override fun onEntry() {
  }

  override fun onExit() {
  }
}
