package ru.kode.way

sealed interface Transition

sealed interface FlowTransition<out R : Any> : Transition
sealed interface ScreenTransition : Transition

data class NavigateTo(
  val targets: Set<Target>,
  val resolution: PathResolution = PathResolution.Relative
) : FlowTransition<Nothing>, ScreenTransition {

  constructor(
    target: Target,
    resolution: PathResolution = PathResolution.Relative
  ) : this(setOf(target), resolution)
}

data class Finish<R : Any>(val result: R) : FlowTransition<R>
data class EnqueueEvent(val event: Event) : FlowTransition<Nothing>, ScreenTransition

/**
 * Consumes event and stays on the current node
 */
object Stay : FlowTransition<Nothing>, ScreenTransition

/**
 * Ignores event and lets any with a defined handler to process it
 */
object Ignore : FlowTransition<Nothing>, ScreenTransition

enum class PathResolution {
  Absolute,
  Relative
}
