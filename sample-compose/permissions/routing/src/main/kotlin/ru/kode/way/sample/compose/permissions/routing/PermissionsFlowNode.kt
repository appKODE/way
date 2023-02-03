package ru.kode.way.sample.compose.permissions.routing

import ru.kode.way.Finish
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.NavigateTo
import ru.kode.way.Target
import ru.kode.way.sample.compose.permissions.domain.PermissionsService
import ru.kode.way.sample.compose.permissions.ui.routing.PermissionsFlowEvent
import javax.inject.Inject

class PermissionsFlowNode @Inject constructor(
  private val service: PermissionsService,
) : FlowNode<PermissionsFlowEvent, PermissionsFlowResult> {

  override val initial = Target.permissions.request
  override val dismissResult = PermissionsFlowResult.Dismissed

  override fun transition(event: PermissionsFlowEvent): FlowTransition<PermissionsFlowResult> {
    return when (event) {
      PermissionsFlowEvent.AllGranted -> Finish(PermissionsFlowResult.AllGranted)
      PermissionsFlowEvent.IntroDone -> NavigateTo(Target.permissions.request)
      PermissionsFlowEvent.PartiallyGranted -> Finish(PermissionsFlowResult.PartiallyGranted)
    }
  }

  override fun onEntry() {
    service.start()
  }

  override fun onExit() {
    service.stop()
  }
}
