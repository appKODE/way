package ru.kode.way

class TestFlowNode(
  initialTarget: Target,
  transitions: List<TestFlowTransitionSpec> = emptyList()
) : GenericTestFlowNode<Unit>(initialTarget, Unit, transitions)

class TestFlowNodeWithResult<R : Any>(
  initialTarget: Target,
  override val dismissResult: R,
  transitions: List<TestFlowTransitionSpec> = emptyList()
) : GenericTestFlowNode<R>(initialTarget, dismissResult, transitions)

open class GenericTestFlowNode<R : Any>(
  initialTarget: Target,
  override val dismissResult: R,
  private val transitions: List<TestFlowTransitionSpec> = emptyList()
) : FlowNode<TestEvent, R> {

  override val initial: Target = initialTarget

  override fun transition(event: TestEvent): FlowTransition<R> {
    return if (transitions.isEmpty()) Ignore else {
      transitions.find { it.event == event.name }?.transition as FlowTransition<R>? ?: Ignore
    }
  }
}

class TestScreenNode(
  private val transitions: List<TestScreenTransitionSpec> = emptyList()
) : ScreenNode<TestEvent> {
  override fun transition(event: TestEvent): ScreenTransition {
    return if (transitions.isEmpty()) Ignore else {
      transitions.find { it.event == event.name }?.transition ?: Ignore
    }
  }
}
