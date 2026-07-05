package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import ru.kode.way.navhist.NavServiceHistorySchema
import ru.kode.way.navhist.app as appT
import ru.kode.way.navhist.login as loginT
import ru.kode.way.navhist.onboarding as onboardingT

/**
 * End-to-end proof that a `type="history"` node declared in a `.dot` schema produces a working,
 * typed [HistoryTarget] accessor via codegen — no hand-built absolute [Path] required.
 *
 * Fixture (nav-service-history): `app` flow whose initial child flow is `onboarding` (screens
 * `intro` (initial) + `page1`) with a sibling `login` flow (`credentials`). A history child
 * `onboarding -> onboardingHist [type="history"]` generates `Target.onboarding.onboardingHist`,
 * whose absolute path points at the `onboarding` flow (NOT the history node itself).
 */
class HistoryTargetCodegenTest :
  ShouldSpec({

    val schema = NavServiceHistorySchema()

    fun newService(): NavigationService<Unit> = NavigationService(
      TestNodeBuilder(
        schema,
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.appT.onboarding,
            transitions = listOf(
              tr("toLogin", Target.appT.login),
              // The GENERATED history accessor — the whole point of this test.
              tr("histOnboarding", Target.onboardingT.onboardingHist),
            ),
          ),
          "app.onboarding" to TestFlowNodeWithResult(
            initialTarget = Target.onboardingT.intro,
            dismissResult = 0,
            transitions = listOf(
              tr("toPage1", Target.onboardingT.page1),
            ),
          ),
          "app.onboarding.intro" to TestScreenNode(),
          "app.onboarding.page1" to TestScreenNode(),
          "app.login" to TestFlowNode(
            initialTarget = Target.loginT.credentials,
          ),
          "app.login.credentials" to TestScreenNode(),
        ),
      ),
      onFinishRequest = { _: Unit -> Stay },
    )

    should("restore the previously-active child via the generated shallow HistoryTarget accessor") {
      val sut = newService()
      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.onboarding.intro"

        // drill to the NON-default child of onboarding
        sut.sendEvent(TestEvent("toPage1"))
        awaitItem().active shouldBe "app.onboarding.page1"

        // navigate away, exiting the onboarding flow (history is recorded here)
        sut.sendEvent(TestEvent("toLogin"))
        awaitItem().active shouldBe "app.login.credentials"

        // the generated Target.onboarding.onboardingHist restores page1, NOT onboarding's default
        // initial (intro)
        sut.sendEvent(TestEvent("histOnboarding"))
        awaitItem().active shouldBe "app.onboarding.page1"
      }
    }
  })
