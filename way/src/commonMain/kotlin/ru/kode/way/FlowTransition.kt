package ru.kode.way

sealed interface FlowTransition<out R : Any>
sealed interface ScreenTransition

data class NavigateTo(
  val targets: Set<Target>,
  val resolution: PathResolution = PathResolution.Relative
) : FlowTransition<Nothing> {

  constructor(
    target: Target,
    resolution: PathResolution = PathResolution.Relative
  ) : this(setOf(target), resolution)
}

data class Finish<R : Any>(val result: R) : FlowTransition<R>
data class EnqueueEvent(val event: Event) : FlowTransition<Nothing>, ScreenTransition
object Stay : FlowTransition<Nothing>, ScreenTransition

enum class PathResolution {
  Absolute,
  Relative
}
