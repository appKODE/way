package ru.kode.test.app.schema

import ru.kode.test.app.AppFlowResult
import ru.kode.test.app.LoginFlowResult
import ru.kode.test.app.OnboardingFlowResult
import ru.kode.way.FlowTarget
import ru.kode.way.FlowTransition
import ru.kode.way.Path
import ru.kode.way.ScreenTarget
import ru.kode.way.Target
import ru.kode.way.append

public class AppTargets(
  private val prefix: Path? = null,
) {
  public fun login(onFinish: (result: LoginFlowResult) -> FlowTransition<AppFlowResult>):
      FlowTarget<LoginFlowResult, AppFlowResult> = FlowTarget(flowPath(Path("login")), onFinish)

  public fun onboarding(onFinish: (result: OnboardingFlowResult) -> FlowTransition<AppFlowResult>):
      FlowTarget<OnboardingFlowResult, AppFlowResult> =
      FlowTarget(flowPath(Path("login","credentials","onboarding")), onFinish)

  private fun flowPath(path: Path): Path = prefix?.append(path) ?: path
}

public class LoginTargets(
  private val prefix: Path? = null,
) {
  public val credentials: ScreenTarget = ScreenTarget(flowPath(Path("credentials")))

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
