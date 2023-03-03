package ru.kode.way

sealed interface Transition

sealed interface FlowTransition<out R : Any> : Transition
sealed interface ScreenTransition : Transition

data class NavigateTo(
  val targets: Set<Target>,
) : FlowTransition<Nothing>, ScreenTransition {

  constructor(
    target: Target,
  ) : this(setOf(target))
}

data class Finish<R : Any>(val result: R) : FlowTransition<R>
data class EnqueueEvent(val event: Event) : FlowTransition<Nothing>, ScreenTransition

/**
 * Consumes event and stays on the current node
 */
object Stay : FlowTransition<Nothing>, ScreenTransition

/**
 * Ignores event and lets any parent node with a defined handler to process it
 */
object Ignore : FlowTransition<Nothing>, ScreenTransition
