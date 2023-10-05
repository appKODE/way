package ru.kode.way.sample.app.routing

import ru.kode.way.Event
import ru.kode.way.Finish
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.Ignore
import ru.kode.way.NavigateTo
import ru.kode.way.Target
import ru.kode.way.sample.app
import ru.kode.way.sample.core.routing.FlowResult

class AppFlowNode : FlowNode<AppFlowResult> {
  override fun transition(event: Event): FlowTransition<AppFlowResult> {
    return Ignore
  }

  override val dismissResult: AppFlowResult = AppFlowResult.Dismissed

  override val initial = Target.app.permissions { result ->
    when (result) {
      FlowResult.Done -> NavigateTo(Target.app.main(onFinishRequest = { Finish(AppFlowResult.Dismissed) }))
      FlowResult.Dismissed -> Finish(AppFlowResult.Dismissed)
    }
  }

  override fun onEntry() {
  }

  override fun onExit() {
  }
}
