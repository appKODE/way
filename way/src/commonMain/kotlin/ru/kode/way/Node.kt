package ru.kode.way

sealed interface Node {
  fun onEntry() = Unit
  fun onExit() = Unit
}

interface FlowNode<R : Any> : Node {
  val initial: Target
  val dismissResult: R
  fun transition(event: Event): FlowTransition<R>
  fun onActiveChildChanged(path: Path, child: Node) = Unit
}

interface ScreenNode : Node {
  fun transition(event: Event): ScreenTransition
}
