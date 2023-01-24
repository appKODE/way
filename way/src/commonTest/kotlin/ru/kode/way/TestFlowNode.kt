package ru.kode.way

class TestFlowNode(
  initialTarget: Target,
  transitions: List<TestTransitionSpec> = emptyList()
) : GenericTestFlowNode<Unit>(initialTarget, Unit, transitions)

class TestFlowNodeWithResult<R : Any>(
  initialTarget: Target,
  override val dismissResult: R,
  transitions: List<TestTransitionSpec> = emptyList()
) : GenericTestFlowNode<R>(initialTarget, dismissResult, transitions)

open class GenericTestFlowNode<R : Any>(
  initialTarget: Target,
  override val dismissResult: R,
  private val transitions: List<TestTransitionSpec> = emptyList()
) : FlowNode<TestEvent, R> {

  override val initial: Target = initialTarget

  override fun transition(event: TestEvent): FlowTransition<R> {
    return if (transitions.isEmpty()) Ignore else {
      val spec = transitions.find { it.event == event.name }
      val target = spec?.target
      if (target != null) {
        NavigateTo(target)
      } else if (spec?.finishResult != null) {
        Finish(spec.finishResult as R)
      } else {
        Ignore
      }
    }
  }
}

class TestScreenNode(
  private val transitions: List<TestTransitionSpec> = emptyList()
) : ScreenNode<TestEvent> {
  override fun transition(event: TestEvent): ScreenTransition {
    return if (transitions.isEmpty()) Ignore else {
      val target = transitions.find { it.event == event.name }?.target
      if (target != null) {
        NavigateTo(target)
      } else Ignore
    }
  }
}
