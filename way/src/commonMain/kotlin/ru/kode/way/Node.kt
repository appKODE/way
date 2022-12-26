package ru.kode.way

sealed interface Node {
  fun onEntry() = Unit
  fun onExit() = Unit
}

interface FlowNode<in E : Event, R : Any> : Node {
  val initial: Target
  val dismissResult: R
  fun transition(event: E): FlowTransition<R>
}

interface ScreenNode<E : Event> : Node {
  fun transition(event: E): ScreenTransition
}
