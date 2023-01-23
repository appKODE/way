package ru.kode.way

sealed interface Target {
  val path: Path

  companion object
}

data class ScreenTarget(override val path: Path) : Target
data class FlowTarget<R1 : Any, R2 : Any>(override val path: Path, val onFinish: OnFinishHandler<R1, R2>) : Target

typealias OnFinishHandler<R1, R2> = (R1) -> FlowTransition<R2>
