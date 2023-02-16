package ru.kode.way.sample.compose.main.routing

import ru.kode.way.Event
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.Stay
import ru.kode.way.Target
import ru.kode.way.sample.compose.main.ui.routing.MainFlowEvent
import ru.kode.way.whenFlowEvent
import javax.inject.Inject

class MainFlowNode @Inject constructor() : FlowNode<MainFlowResult> {

  override val initial = Target.main.home
  override val dismissResult = MainFlowResult.Dismissed

  override fun transition(event: Event): FlowTransition<MainFlowResult> {
    return event.whenFlowEvent { e: MainFlowEvent ->
      when (e) {
        MainFlowEvent.EditProfileRequested -> Stay
      }
    }
  }
}
