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
) : FlowNode<R> {

  override val initial: Target = initialTarget

  override fun transition(event: Event): FlowTransition<R> {
    return event.whenFlowEvent { e: TestEvent ->
      if (transitions.isEmpty()) Ignore else {
        transitions.find { it.event == e.name }?.transition as FlowTransition<R>? ?: Ignore
      }
    }
  }
}

class TestScreenNode(
  private val transitions: List<TestScreenTransitionSpec> = emptyList()
) : ScreenNode {
  override fun transition(event: Event): ScreenTransition {
    return event.whenScreenEvent { e: TestEvent ->
      if (transitions.isEmpty()) Ignore else {
        transitions.find { it.event == e.name }?.transition ?: Ignore
      }
    }
  }
}
