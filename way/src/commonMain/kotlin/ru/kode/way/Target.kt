package ru.kode.way

sealed interface Target {
  val path: Path
  val payload: Any?

  companion object
}

data class ScreenTarget(override val path: Path, override val payload: Any? = null) : Target
data class FlowTarget<R1 : Any, R2 : Any>(
  override val path: Path,
  override val payload: Any? = null,
  val onFinish: OnFinishHandler<R1, R2>
) : Target

typealias OnFinishHandler<R1, R2> = (R1) -> FlowTransition<R2>
