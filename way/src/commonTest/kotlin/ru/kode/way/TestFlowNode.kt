package ru.kode.way

class TestFlowNode(
  initialTarget: Target,
) : FlowNode<Event, Unit> {
  constructor(
    initialScreen: String,
    vararg segments: String,
  ) : this(ScreenTarget(Path(initialScreen, *segments)))

  override val initial: Target = initialTarget
  override val dismissResult: Unit = Unit

  override fun transition(event: Event): FlowTransition<Unit> {
    return Stay
  }
}

class TestScreenNode() : ScreenNode {
  override fun transition(event: Event): ScreenTransition {
    return Stay
  }
}
