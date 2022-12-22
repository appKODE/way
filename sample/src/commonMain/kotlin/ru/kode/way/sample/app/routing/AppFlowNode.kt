package ru.kode.way.sample.app.routing

import ru.kode.way.Event
import ru.kode.way.Finish
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.NavigateTo
import ru.kode.way.Path
import ru.kode.way.Stay
import ru.kode.way.Target
import ru.kode.way.sample.core.routing.FlowResult

class AppFlowNode : FlowNode<Event, AppFlowResult> {
  override fun transition(event: Event): FlowTransition<AppFlowResult> {
    return Stay
  }

  override val dismissResult: AppFlowResult = AppFlowResult.Dismissed

  override val initial: Target = Path.app.permissions(
    onFinish = { result ->
      when (result) {
        FlowResult.Done -> NavigateTo(Path.app.main)
        FlowResult.Dismissed -> Finish(AppFlowResult.Dismissed)
      }

    }
  )

  override fun onEntry() {
    TODO("Not yet implemented")
  }

  override fun onExit() {
    TODO("Not yet implemented")
  }
}
