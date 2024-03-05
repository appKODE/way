package ru.kode.way

class TestFlowNode(
  initialTarget: Target,
  private val onEntryImpl: () -> Unit = {},
  private val onExitImpl: () -> Unit = {},
  transitions: List<TestFlowTransitionSpec> = emptyList(),
  val payload: Any? = null,
) : GenericTestFlowNode<Unit>(initialTarget, Unit, onEntryImpl, onExitImpl, transitions)

class TestFlowNodeWithResult<R : Any>(
  initialTarget: Target,
  override val dismissResult: R,
  private val onEntryImpl: () -> Unit = {},
  private val onExitImpl: () -> Unit = {},
  transitions: List<TestFlowTransitionSpec> = emptyList()
) : GenericTestFlowNode<R>(initialTarget, dismissResult, onEntryImpl, onExitImpl, transitions)

open class GenericTestFlowNode<R : Any>(
  initialTarget: Target,
  override val dismissResult: R,
  private val onEntryImpl: () -> Unit = {},
  private val onExitImpl: () -> Unit = {},
  private val transitions: List<TestFlowTransitionSpec> = emptyList(),
) : FlowNode<R> {

  override val initial: Target = initialTarget

  override fun transition(event: Event): FlowTransition<R> {
    return if (transitions.isEmpty()) Ignore else {
      transitions.find { it.eventMatcher(event) }?.transition as FlowTransition<R>? ?: Ignore
    }
  }

  override fun onEntry() {
    super.onEntry()
    onEntryImpl()
  }

  override fun onExit() {
    super.onExit()
    onExitImpl()
  }
}

class TestScreenNode(
  val payload: Any? = null,
  private val transitions: List<TestScreenTransitionSpec> = emptyList(),
  private val onEntryImpl: () -> Unit = {},
  private val onExitImpl: () -> Unit = {},
) : ScreenNode {
  override fun transition(event: Event): ScreenTransition {
    return if (transitions.isEmpty()) Ignore else {
      transitions.find { it.eventMatcher(event) }?.transition ?: Ignore
    }
  }

  override fun onEntry() {
    super.onEntry()
    onEntryImpl()
  }

  override fun onExit() {
    super.onExit()
    onExitImpl()
  }
}

class TestParallelNode(
  val payload: Any? = null,
  private val transitions: List<TestFlowTransitionSpec> = emptyList(),
  private val onEntryImpl: () -> Unit = {},
  private val onExitImpl: () -> Unit = {},
) : ParallelNode {
  override val backDispatchStrategy: BackDispatchStrategy
    get() = TODO("create fake for this or use some default implementation")

  override fun transition(event: Event): FlowTransition<Unit> {
    return event.whenFlowEvent { e: TestEvent ->
      if (transitions.isEmpty()) Ignore else {
        transitions.find { it.eventMatcher(e) }?.transition as FlowTransition<Unit>? ?: Ignore
      }
    }
  }
}
