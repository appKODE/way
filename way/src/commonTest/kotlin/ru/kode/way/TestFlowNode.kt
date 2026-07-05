package ru.kode.way

class TestFlowNode(
  initialTarget: Target,
  onEntryImpl: () -> Unit = {},
  onExitImpl: () -> Unit = {},
  onDisposeImpl: () -> Unit = {},
  transitions: List<TestFlowTransitionSpec> = emptyList(),
  val payload: Any? = null,
) : GenericTestFlowNode<Unit>(initialTarget, Unit, onEntryImpl, onExitImpl, onDisposeImpl, transitions)

class TestFlowNodeWithResult<R : Any>(
  initialTarget: Target,
  override val dismissResult: R,
  onEntryImpl: () -> Unit = {},
  onExitImpl: () -> Unit = {},
  onDisposeImpl: () -> Unit = {},
  transitions: List<TestFlowTransitionSpec> = emptyList(),
) : GenericTestFlowNode<R>(initialTarget, dismissResult, onEntryImpl, onExitImpl, onDisposeImpl, transitions)

open class GenericTestFlowNode<R : Any>(
  initialTarget: Target,
  override val dismissResult: R,
  private val onEntryImpl: () -> Unit = {},
  private val onExitImpl: () -> Unit = {},
  private val onDisposeImpl: () -> Unit = {},
  private val transitions: List<TestFlowTransitionSpec> = emptyList(),
) : FlowNode<R> {

  override val initial: Target = initialTarget

  override fun transition(event: Event): FlowTransition<R> = if (transitions.isEmpty()) {
    Ignore
  } else {
    transitions.find { it.eventMatcher(event) }?.transition as FlowTransition<R>? ?: Ignore
  }

  override fun onEntry(event: Event) {
    super.onEntry(event)
    onEntryImpl()
  }

  override fun onExit(event: Event) {
    super.onExit(event)
    onExitImpl()
  }

  override fun onDispose() {
    super.onDispose()
    onDisposeImpl()
  }
}

class TestScreenNode(
  val payload: Any? = null,
  private val transitions: List<TestScreenTransitionSpec> = emptyList(),
  private val onEntryImpl: () -> Unit = {},
  private val onExitImpl: () -> Unit = {},
  private val onDisposeImpl: () -> Unit = {},
) : ScreenNode {
  override fun transition(event: Event): ScreenTransition = if (transitions.isEmpty()) {
    Ignore
  } else {
    transitions.find { it.eventMatcher(event) }?.transition ?: Ignore
  }

  override fun onEntry(event: Event) {
    super.onEntry(event)
    onEntryImpl()
  }

  override fun onExit(event: Event) {
    super.onExit(event)
    onExitImpl()
  }

  override fun onDispose() {
    super.onDispose()
    onDisposeImpl()
  }
}

class TestParallelNode(
  val payload: Any? = null,
  private val transitions: List<TestFlowTransitionSpec> = emptyList(),
  private val parallelTransitions: List<TestParallelTransitionSpec> = emptyList(),
  private val onEntryImpl: () -> Unit = {},
  private val onExitImpl: () -> Unit = {},
  private val onDisposeImpl: () -> Unit = {},
  private val onTransitionCallback: ((Event) -> Unit)? = null,
  // Stateful override for Back: each Back event pops the next transition off this queue. Lets a test
  // drive an Ignore-then-Finish sequence to exercise the re-consultation inside dispatchBackThroughParallel.
  private val backTransitionQueue: MutableList<FlowTransition<Unit>>? = null,
) : ParallelFlowNode<Unit>() {
  override val dismissResult = Unit

  override fun transition(event: Event): FlowTransition<Unit> {
    onTransitionCallback?.invoke(event)
    if (event is BackEvent && backTransitionQueue != null && backTransitionQueue.isNotEmpty()) {
      return backTransitionQueue.removeAt(0)
    }
    parallelTransitions.find { it.eventMatcher(event) }?.transition?.also { return it }
    return if (event is TestEvent) {
      if (transitions.isEmpty()) {
        Ignore
      } else {
        transitions.find { it.eventMatcher(event) }?.transition as FlowTransition<Unit>? ?: Ignore
      }
    } else {
      Ignore
    }
  }

  override fun onEntry(event: Event) {
    super.onEntry(event)
    onEntryImpl()
  }

  override fun onExit(event: Event) {
    super.onExit(event)
    onExitImpl()
  }

  override fun onDispose() {
    super.onDispose()
    onDisposeImpl()
  }
}

data class TestParallelTransitionSpec(val eventMatcher: (Event) -> Boolean, val transition: FlowTransition<Unit>)

inline fun <reified E : Event> trp(transition: FlowTransition<Unit>): TestParallelTransitionSpec =
  TestParallelTransitionSpec(eventMatcher = { it is E }, transition)
