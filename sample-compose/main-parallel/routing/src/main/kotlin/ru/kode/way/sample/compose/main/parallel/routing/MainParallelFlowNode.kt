package ru.kode.way.sample.compose.main.parallel.routing

import ru.kode.way.BackDispatchStrategy
import ru.kode.way.Event
import ru.kode.way.FlowTransition
import ru.kode.way.ParallelNode
import javax.inject.Inject

class MainParallelFlowNode @Inject constructor() : ParallelNode {
  override val backDispatchStrategy: BackDispatchStrategy
    get() = TODO("Not yet implemented")

  override fun transition(event: Event): FlowTransition<Unit> {
    TODO("not implemented")
  }
}
