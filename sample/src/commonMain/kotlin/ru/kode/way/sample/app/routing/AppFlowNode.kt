package ru.kode.way.sample.app.routing

import ru.kode.way.Event
import ru.kode.way.Finish
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.Ignore
import ru.kode.way.NavigateTo
import ru.kode.way.Target
import ru.kode.way.sample.AppChildFinishRequest
import ru.kode.way.sample.app
import ru.kode.way.sample.core.routing.FlowResult

class AppFlowNode : FlowNode<AppFlowResult> {
  override val dismissResult: AppFlowResult = AppFlowResult.Dismissed

  override val initial = Target.app.permissions

  override fun transition(event: Event): FlowTransition<AppFlowResult> {
    return when (event) {
      is AppChildFinishRequest.Permissions -> {
        when (event.result) {
          FlowResult.Done -> NavigateTo(Target.app.main)
          FlowResult.Dismissed -> Finish(AppFlowResult.Dismissed)
        }
      }
      is AppChildFinishRequest.Main -> {
        Finish(AppFlowResult.Dismissed)
      }
      else -> Ignore
    }
  }

  override fun onEntry() = Unit

  override fun onExit() = Unit
}
