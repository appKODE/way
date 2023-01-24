package ru.kode.test.app.schema

import ru.kode.test.app.FlowResult
import ru.kode.test.permissions.PermissionFlowResult
import ru.kode.way.FlowTarget
import ru.kode.way.FlowTransition
import ru.kode.way.Path
import ru.kode.way.ScreenTarget
import ru.kode.way.Target
import ru.kode.way.append

public class AppTargets(
  private val prefix: Path? = null,
) {
  public val permissions: PermissionsTargets = PermissionsTargets(flowPath(Path("permissions")))

  public val screen4: ScreenTarget = ScreenTarget(flowPath(Path("screen4")))

  public val screen1: ScreenTarget = ScreenTarget(flowPath(Path("screen1")))

  public val screen2: ScreenTarget = ScreenTarget(flowPath(Path("screen1","screen2")))

  public val screen3: ScreenTarget = ScreenTarget(flowPath(Path("screen1","screen2","screen3")))

  public fun permissions(onFinish: (result: PermissionFlowResult) -> FlowTransition<FlowResult>):
      FlowTarget<PermissionFlowResult, FlowResult> = FlowTarget(flowPath(Path("permissions")),
      onFinish)

  private fun flowPath(path: Path): Path = prefix?.append(path) ?: path
}

public class PermissionsTargets(
  private val prefix: Path? = null,
) {
  public val finish: ScreenTarget = ScreenTarget(flowPath(Path("finish")))

  public val intro: ScreenTarget = ScreenTarget(flowPath(Path("intro")))

  public val page1: ScreenTarget = ScreenTarget(flowPath(Path("intro","page1")))

  private fun flowPath(path: Path): Path = prefix?.append(path) ?: path
}

public val Target.Companion.app: AppTargets
  get() = AppTargets()

public val Target.Companion.permissions: PermissionsTargets
  get() = PermissionsTargets()
