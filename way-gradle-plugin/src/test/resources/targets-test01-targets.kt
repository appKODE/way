package ru.kode.test.app.scheme

object AppTargets {
  val screen1: ScreenTarget = ScreenTarget(Path("screen1"))
  val screen2: ScreenTarget = ScreenTarget(Path("screen1", "screen2"))
  val screen3: ScreenTarget = ScreenTarget(Path("screen1", "screen2", "screen3"))
  val screen4: ScreenTarget = ScreenTarget(Path("screen4"))

  fun permissions(onFinish: (FlowResult) -> FlowTransition<AppFlowResult>): FlowTarget<FlowResult, AppFlowResult> {
    return FlowTarget(Path("permissions"), onFinish)
  }
}

object PermissionTargets {
  val intro: ScreenTarget = ScreenTarget(Path("intro"))
  val page1: ScreenTarget = ScreenTarget(Path("intro", "page1"))
  val finish: ScreenTarget = ScreenTarget(Path("finish"))
}

val Target.Companion.app get() = AppTargets
