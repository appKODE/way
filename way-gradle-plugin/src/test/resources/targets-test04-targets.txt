package ru.kode.test.app.schema

import java.nio.Charset
import kotlin.Boolean
import kotlin.Int
import kotlin.Unit
import ru.kode.test.app.AppFlowResult
import ru.kode.way.FlowTarget
import ru.kode.way.FlowTransition
import ru.kode.way.Path
import ru.kode.way.ScreenTarget
import ru.kode.way.Target
import ru.kode.way.append

public class AppTargets(
  private val prefix: Path? = null,
) {
  public val intro: ScreenTarget = ScreenTarget(flowPath(Path("intro")))

  public fun main(userId: Int, onFinish: (result: Unit) -> FlowTransition<AppFlowResult>):
      FlowTarget<Unit, AppFlowResult> = FlowTarget(flowPath(Path("main")), payload = userId,
      onFinish)

  public fun page1(charset: Charset): ScreenTarget = ScreenTarget(flowPath(Path("intro","page1")),
      payload = charset)

  public fun page2(userCount: Int): ScreenTarget =
      ScreenTarget(flowPath(Path("intro","page1","page2")), payload = userCount)

  public fun permissions(requireGrantAll: Boolean,
      onFinish: (result: Unit) -> FlowTransition<AppFlowResult>): FlowTarget<Unit, AppFlowResult> =
      FlowTarget(flowPath(Path("intro","page1","page2","permissions")), payload = requireGrantAll,
      onFinish)

  private fun flowPath(path: Path): Path = prefix?.append(path) ?: path
}

public class MainTargets(
  private val prefix: Path? = null,
) {
  private fun flowPath(path: Path): Path = prefix?.append(path) ?: path
}

public val Target.Companion.app: AppTargets
  get() = AppTargets()

public val Target.Companion.main: MainTargets
  get() = MainTargets()
