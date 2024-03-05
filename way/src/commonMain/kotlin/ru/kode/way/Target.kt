package ru.kode.way

sealed interface Target {
  val path: Path
  val payload: Any?

  companion object
}

data class ScreenTarget(override val path: Path, override val payload: Any? = null) : Target
data class FlowTarget(
  override val path: Path,
  override val payload: Any? = null,
) : Target
