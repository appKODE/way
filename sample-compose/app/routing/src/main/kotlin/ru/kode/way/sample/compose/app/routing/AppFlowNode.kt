package ru.kode.way.sample.compose.app.routing

import ru.kode.way.Event
import ru.kode.way.Finish
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.Ignore
import ru.kode.way.NavigateTo
import ru.kode.way.Target
import ru.kode.way.sample.compose.login.routing.LoginFlowResult
import javax.inject.Inject

class AppFlowNode @Inject constructor() : FlowNode<Unit> {
  override val initial: Target = Target.app.login

  override val dismissResult: Unit = Unit

  override fun transition(event: Event): FlowTransition<Unit> {
    return when (event) {
      is AppChildFinishRequest.Login -> {
        when (event.result) {
          LoginFlowResult.Success -> {
            NavigateTo(Target.app.main)
          }
          LoginFlowResult.Dismissed -> {
            Finish(Unit)
          }
        }
      }
      is AppChildFinishRequest.Main -> Finish(Unit)
      else -> Ignore
    }
  }
}
