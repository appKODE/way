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
    return if (transitions.isEmpty()) Stay else {
      val target = transitions.find { it.event == event.name }?.let { ScreenTarget(Path(it.target)) }
      if (target != null) {
        NavigateTo(target)
      } else {
        println("no transition for event \"${event.name}\", ignoring")
        Stay
      }
    }
  }
}

class TestScreenNode(

) : ScreenNode {
  override fun transition(event: Event): ScreenTransition {
    return Stay
  }
}
