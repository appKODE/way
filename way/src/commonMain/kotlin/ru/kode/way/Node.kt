package ru.kode.way

interface Node {
  fun onEntry()
  fun onExit()
}

interface FlowNode<E : Event, R : Any> : Node {
  fun transition(event: E): FlowTransition<R>

  val dismissResult: R
  val initial: Target
}

interface ScreenNode : Node {
  fun transition(event: Event): ScreenTransition
}
