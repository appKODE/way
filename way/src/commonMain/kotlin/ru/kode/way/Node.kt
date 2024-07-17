package ru.kode.way

sealed interface Node {
  fun onEntry(event: Event) = Unit
  fun onExit(event: Event) = Unit
}

interface FlowNode<R : Any> : Node {
  val initial: Target
  val dismissResult: R
  fun transition(event: Event): FlowTransition<R>
}

interface ParallelNode : Node {
  val backDispatchStrategy: BackDispatchStrategy
  fun transition(event: Event): FlowTransition<Unit>
}

interface ScreenNode : Node {
  fun transition(event: Event): ScreenTransition
}

interface BackDispatchStrategy {
  fun choose(activePaths: Map<RegionId, Path>): Path
}
