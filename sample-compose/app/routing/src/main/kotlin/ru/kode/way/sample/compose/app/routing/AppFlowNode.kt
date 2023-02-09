package ru.kode.way.sample.compose.app.routing

import ru.kode.way.Event
import ru.kode.way.Finish
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.Ignore
import ru.kode.way.Target
import javax.inject.Inject

class AppFlowNode @Inject constructor() : FlowNode<Event, Unit> {
  override val initial: Target = Target.app.login { Finish(Unit) }
  override val dismissResult: Unit = Unit

  override fun transition(event: Event): FlowTransition<Unit> {
    return Ignore
  }
}
