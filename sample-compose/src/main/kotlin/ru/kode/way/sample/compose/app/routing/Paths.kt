package ru.kode.way.sample.compose.app.routing

import ru.kode.way.FlowTarget
import ru.kode.way.FlowTransition
import ru.kode.way.Path
import ru.kode.way.ScreenTarget
import ru.kode.way.sample.compose.core.routing.FlowResult

object AppPaths {
  fun permissions(onFinish: (FlowResult) -> FlowTransition<AppFlowResult>): FlowTarget<FlowResult, AppFlowResult> {
    return FlowTarget(Path("permissions"), onFinish)
  }

  val main: ScreenTarget = ScreenTarget(Path("main"))
}

interface PathWithFinish

val Path.Companion.app get() = AppPaths
