package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import ru.kode.way.nav08.NavService08Schema
import ru.kode.way.nav08.app as app08
import ru.kode.way.nav08.login as login08
import ru.kode.way.nav08.onboarding as onboarding08

/**
 * Runtime coverage for SCXML-style [HistoryTarget] restoration.
 *
 * Fixture (nav-service08): `app` flow whose initial child flow is `onboarding` (screens `intro`
 * (initial) + `page1`) with a sibling `login` flow (screen `credentials`). Navigating away from
 * `onboarding` to `login` exits the `onboarding` flow, which records its history.
 */
class HistoryTargetTest :
  ShouldSpec({

    // Absolute paths built from the schema so their Segment ids (with @file disambiguators) match
    // the runtime region/node paths — a hand-typed Path("app", "onboarding") would not.
    val schema = NavService08Schema()
    val onboardingPath = AbsoluteTarget(schema.rootSegment, Target.app08.onboarding).path
    val loginPath = AbsoluteTarget(schema.rootSegment, Target.app08.login).path

    fun newService(): NavigationService<Unit> = NavigationService(
      TestNodeBuilder(
        schema,
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app08.onboarding,
            transitions = listOf(
              tr("toLogin", Target.app08.login),
              tr("histOnboardingShallow", HistoryTarget(onboardingPath, deep = false)),
              tr("histOnboardingDeep", HistoryTarget(onboardingPath, deep = true)),
              tr("histLoginShallow", HistoryTarget(loginPath, deep = false)),
            ),
          ),
          "app.onboarding" to TestFlowNodeWithResult(
            initialTarget = Target.onboarding08.intro,
            dismissResult = 0,
            transitions = listOf(
              tr("toPage1", Target.onboarding08.page1),
            ),
          ),
          "app.onboarding.intro" to TestScreenNode(),
          "app.onboarding.page1" to TestScreenNode(),
          "app.login" to TestFlowNode(
            initialTarget = Target.login08.credentials,
          ),
          "app.login.credentials" to TestScreenNode(),
        ),
      ),
      onFinishRequest = { _: Unit -> Stay },
    )

    should("restore the previously-active child on shallow HistoryTarget") {
      val sut = newService()
      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.onboarding.intro"

        // drill to the NON-default child of onboarding
        sut.sendEvent(TestEvent("toPage1"))
        awaitItem().active shouldBe "app.onboarding.page1"

        // navigate away, exiting the onboarding flow (history is recorded here)
        sut.sendEvent(TestEvent("toLogin"))
        awaitItem().active shouldBe "app.login.credentials"

        // shallow history restores the previously-active child (page1), NOT onboarding's default
        // initial (intro)
        sut.sendEvent(TestEvent("histOnboardingShallow"))
        awaitItem().active shouldBe "app.onboarding.page1"
      }
    }

    should("restore the previously-active leaf on deep HistoryTarget") {
      val sut = newService()
      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.onboarding.intro"

        sut.sendEvent(TestEvent("toPage1"))
        awaitItem().active shouldBe "app.onboarding.page1"

        sut.sendEvent(TestEvent("toLogin"))
        awaitItem().active shouldBe "app.login.credentials"

        // deep history restores the recorded atomic leaf (page1)
        sut.sendEvent(TestEvent("histOnboardingDeep"))
        awaitItem().active shouldBe "app.onboarding.page1"
      }
    }

    should("fall back to the default initial when the flow has no recorded history") {
      val sut = newService()
      sut.collectTransitions().test {
        // login flow has never been entered, so it has no recorded history
        awaitItem().active shouldBe "app.onboarding.intro"

        // HistoryTarget(app.login) with no history behaves like FlowTarget(app.login):
        // enters login's default initial (credentials)
        sut.sendEvent(TestEvent("histLoginShallow"))
        awaitItem().active shouldBe "app.login.credentials"
      }
    }

    should("record fresh history each time the flow is exited") {
      val sut = newService()
      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.onboarding.intro"

        // exit onboarding while its active child is the DEFAULT (intro)
        sut.sendEvent(TestEvent("toLogin"))
        awaitItem().active shouldBe "app.login.credentials"

        // shallow history restores intro (the child active at the last exit)
        sut.sendEvent(TestEvent("histOnboardingShallow"))
        awaitItem().active shouldBe "app.onboarding.intro"

        // drill to page1, then exit onboarding again
        sut.sendEvent(TestEvent("toPage1"))
        awaitItem().active shouldBe "app.onboarding.page1"
        sut.sendEvent(TestEvent("toLogin"))
        awaitItem().active shouldBe "app.login.credentials"

        // history now reflects the newer active child (page1)
        sut.sendEvent(TestEvent("histOnboardingShallow"))
        awaitItem().active shouldBe "app.onboarding.page1"
      }
    }
  })
