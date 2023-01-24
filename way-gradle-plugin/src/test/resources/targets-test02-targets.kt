package ru.kode.test.app.schema

import kotlin.Unit
import ru.kode.way.FlowTarget
import ru.kode.way.FlowTransition
import ru.kode.way.Path
import ru.kode.way.ScreenTarget
import ru.kode.way.Target
import ru.kode.way.append

public class AppTargets(
  private val prefix: Path? = null,
) {
  public val login: LoginTargets = LoginTargets(flowPath(Path("login")))

  public fun login(onFinish: (result: Unit) -> FlowTransition<Unit>): FlowTarget<Unit, Unit> =
      FlowTarget(flowPath(Path("login")), onFinish)

  private fun flowPath(path: Path): Path = prefix?.append(path) ?: path
}

public class LoginTargets(
  private val prefix: Path? = null,
) {
  public val onboarding: OnboardingTargets = OnboardingTargets(flowPath(Path("onboarding")))

  public fun onboarding(onFinish: (result: Unit) -> FlowTransition<Unit>): FlowTarget<Unit, Unit> =
      FlowTarget(flowPath(Path("onboarding")), onFinish)

  private fun flowPath(path: Path): Path = prefix?.append(path) ?: path
}

public class OnboardingTargets(
  private val prefix: Path? = null,
) {
  public val intro: ScreenTarget = ScreenTarget(flowPath(Path("intro")))

  private fun flowPath(path: Path): Path = prefix?.append(path) ?: path
}

public val Target.Companion.app: AppTargets
  get() = AppTargets()

public val Target.Companion.login: LoginTargets
  get() = LoginTargets()

public val Target.Companion.onboarding: OnboardingTargets
  get() = OnboardingTargets()
