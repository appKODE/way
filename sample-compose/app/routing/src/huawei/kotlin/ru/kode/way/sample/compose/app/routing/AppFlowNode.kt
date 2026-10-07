package ru.kode.way.sample.compose.app.routing

import ru.kode.way.Event
import ru.kode.way.Finish
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.Ignore
import ru.kode.way.Target
import javax.inject.Inject

// The login step is added by the google flavor's schema extension (src/google/way/app-flow.dot),
// so this flow starts directly at "main" instead of "login".
class AppFlowNode @Inject constructor() : FlowNode<Unit> {
  override val initial: Target = Target.app.main

  override val dismissResult: Unit = Unit

  override fun transition(event: Event): FlowTransition<Unit> = when (event) {
    is AppChildFinishRequest.Main -> Finish(Unit)
    else -> Ignore
  }
}
