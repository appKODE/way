package ru.kode.way

sealed interface Target {
  val path: Path
}

data class ScreenTarget(override val path: Path) : Target
data class FlowTarget<R1 : Any, R2 : Any>(override val path: Path, val onFinish: (R1) -> FlowTransition<R2>) : Target
