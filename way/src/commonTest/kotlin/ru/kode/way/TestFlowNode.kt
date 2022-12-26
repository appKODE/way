package ru.kode.way

class TestFlowNode(
  initialTarget: Target,
  private val transitions: List<TestTransitionSpec> = emptyList()
) : FlowNode<TestEvent, Unit> {
  constructor(
    initialScreen: String,
    vararg segments: String,
    transitions: List<TestTransitionSpec> = emptyList()
  ) : this(ScreenTarget(Path(initialScreen, *segments)), transitions)

  override val initial: Target = initialTarget
  override val dismissResult: Unit = Unit

  override fun transition(event: TestEvent): FlowTransition<Unit> {
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
