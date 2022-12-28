package ru.kode.way

class TestFlowNode(
  initialTarget: Target,
  private val transitions: List<TestTransitionSpec> = emptyList()
) : GenericTestFlowNode<Unit>(initialTarget, Unit, transitions) {
  constructor(
    initialScreen: String,
    vararg segments: String,
    transitions: List<TestTransitionSpec> = emptyList()
  ) : this(ScreenTarget(Path(initialScreen, *segments)), transitions)
}

class TestFlowNodeWithResult<R : Any>(
  initialTarget: Target,
  override val dismissResult: R,
  private val transitions: List<TestTransitionSpec> = emptyList()
) : GenericTestFlowNode<R>(initialTarget, dismissResult, transitions) {
  constructor(
    initialScreen: String,
    dismissResult: R,
    vararg segments: String,
    transitions: List<TestTransitionSpec> = emptyList()
  ) : this(ScreenTarget(Path(initialScreen, *segments)), dismissResult, transitions)
}

open class GenericTestFlowNode<R : Any>(
  initialTarget: Target,
  override val dismissResult: R,
  private val transitions: List<TestTransitionSpec> = emptyList()
) : FlowNode<TestEvent, R> {
  constructor(
    initialScreen: String,
    dismissResult: R,
    vararg segments: String,
    transitions: List<TestTransitionSpec> = emptyList()
  ) : this(ScreenTarget(Path(initialScreen, *segments)), dismissResult, transitions)

  override val initial: Target = initialTarget

  override fun transition(event: TestEvent): FlowTransition<R> {
    return if (transitions.isEmpty()) Ignore else {
      val spec = transitions.find { it.event == event.name }
      val target = spec
        ?.let {
          if (it.targetScreen != null) {
            ScreenTarget(Path(it.targetScreen))
          } else if (it.targetFlow != null) {
            FlowTarget(Path(it.targetFlow), onFinish = { _: Unit -> Ignore })
          } else {
            null
          }
        }
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
      val target = transitions.find { it.event == event.name }
        ?.let {
          if (it.targetScreen != null) {
            ScreenTarget(Path(it.targetScreen))
          } else if (it.targetFlow != null) {
            FlowTarget(Path(it.targetFlow), onFinish = { _: Unit -> Ignore })
          } else {
            error("either targetScreen or targetFlow must not be empty")
          }
        }
      if (target != null) {
        NavigateTo(target)
      } else {
        Ignore
      }
    }
  }
}
