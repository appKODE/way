package ru.kode.way

interface Node {
  fun onEntry() = Unit
  fun onExit() = Unit
}

interface FlowNode<E : Event, R : Any> : Node {
  val initial: Target
  val dismissResult: R
  fun transition(event: E): FlowTransition<R>
}

interface ScreenNode : Node {
  fun transition(event: Event): ScreenTransition
}
