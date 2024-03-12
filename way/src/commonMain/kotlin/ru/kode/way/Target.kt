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

data class AbsoluteTarget(
  /**
   * An absolute path to the target node
   */
  override val path: Path,
  /**
   * Mapping from the absolute path to payload for nodes with parameters along the path.
   * For example an AbsoluteTarget for target "app.login(x = 33).profile.permissions(y = 42).intro" this map needs to
   * contain entries:
   *
   * ```
   * app.login → 33
   * app.login.profile.permissions → 42
   * ```
   */
  val payloads: Map<Path, Any>
) : Target {
  override val payload: Any? = null
}
