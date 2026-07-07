package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import ru.kode.way.navhistnested.NavServiceHistoryNestedSchema
import ru.kode.way.navhistnested.app as appN
import ru.kode.way.navhistnested.login as loginN
import ru.kode.way.navhistnested.wizard as wizardN

/**
 * Runtime coverage that DISCRIMINATES shallow vs deep [HistoryTarget] using a NESTED (3-level) flow.
 *
 * Fixture (nav-service-history-nested): root flow `app` -> child flow `onboarding` -> grandchild
 * flow `wizard` with screens `step1` (initial) + `step2`; plus a sibling flow `login` (screen
 * `credentials`). Because `onboarding`'s active immediate child (`wizard`) is itself a compound flow
 * that can be drilled to a NON-default leaf (`step2`), shallow and deep history restoration diverge:
 *
 *   - SHALLOW restores WHICH immediate child of `onboarding` was active (`wizard`), then that child
 *     re-enters at ITS OWN default (`step1`) — the recorded `step2` is forgotten.
 *   - DEEP restores the exact atomic leaf that was active at exit (`step2`).
 *
 * The flat fixture in [HistoryTargetTest] (nav-service08) cannot show this: there `onboarding`'s
 * immediate child IS a leaf screen, so its shallow and deep cases both restore the same leaf.
 */
class HistoryTargetNestedTest :
  ShouldSpec({

    // Absolute paths built from the schema so their Segment ids (with @file disambiguators) match
    // the runtime region/node paths — a hand-typed Path("app", "onboarding") would not.
    val schema = NavServiceHistoryNestedSchema()
    val onboardingPath = AbsoluteTarget(schema.rootSegment, Target.appN.onboarding).path

    // `onboarding`'s initial must point to its child flow `wizard` with a RELATIVE single-segment
    // path ([wizard]); the runtime appends it to onboarding's absolute path. The generated
    // `Target.app.wizard` carries the app-relative path [onboarding, wizard], so take just its last
    // segment to get the relative `wizard` hop onboarding needs.
    val wizardSegment = Target.appN.wizard.path.lastSegment()

    fun newService(): NavigationService<Unit> = NavigationService(
      TestNodeBuilder(
        schema,
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.appN.onboarding,
            transitions = listOf(
              tr("toLogin", Target.appN.login),
              tr("histShallow", HistoryTarget(onboardingPath, deep = false)),
              tr("histDeep", HistoryTarget(onboardingPath, deep = true)),
            ),
          ),
          "app.onboarding" to TestFlowNode(
            initialTarget = FlowTarget(Path(wizardSegment)),
          ),
          "app.onboarding.wizard" to TestFlowNode(
            initialTarget = Target.wizardN.step1,
            transitions = listOf(
              tr("toStep2", Target.wizardN.step2),
            ),
          ),
          "app.onboarding.wizard.step1" to TestScreenNode(),
          "app.onboarding.wizard.step2" to TestScreenNode(),
          "app.login" to TestFlowNode(
            initialTarget = Target.loginN.credentials,
          ),
          "app.login.credentials" to TestScreenNode(),
        ),
      ),
      onFinishRequest = { _: Unit -> Stay },
    )

    should("shallow history restores the intermediate flow at its OWN default, not the recorded leaf") {
      val sut = newService()
      sut.collectTransitions().test {
        // initial drill-in: app -> onboarding -> wizard -> step1 (wizard's default)
        awaitItem().active shouldBe "app.onboarding.wizard.step1"

        // drill wizard to its NON-default leaf
        sut.sendEvent(TestEvent("toStep2"))
        awaitItem().active shouldBe "app.onboarding.wizard.step2"

        // navigate away, exiting the whole onboarding subtree (history recorded here, with wizard@step2)
        sut.sendEvent(TestEvent("toLogin"))
        awaitItem().active shouldBe "app.login.credentials"

        // SHALLOW: restores which child of onboarding was active (wizard), but wizard re-enters at
        // its OWN default (step1) — the recorded step2 is intentionally forgotten.
        sut.sendEvent(TestEvent("histShallow"))
        awaitItem().active shouldBe "app.onboarding.wizard.step1"
      }
    }

    should("deep history restores the exact nested leaf") {
      val sut = newService()
      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.onboarding.wizard.step1"

        sut.sendEvent(TestEvent("toStep2"))
        awaitItem().active shouldBe "app.onboarding.wizard.step2"

        sut.sendEvent(TestEvent("toLogin"))
        awaitItem().active shouldBe "app.login.credentials"

        // DEEP: restores the exact atomic leaf that was active at exit (step2).
        sut.sendEvent(TestEvent("histDeep"))
        awaitItem().active shouldBe "app.onboarding.wizard.step2"
      }
    }

    // DISCRIMINATOR: the two cases above run IDENTICAL navigation (drill to step2, exit to login) and
    // differ ONLY in deep=false vs deep=true, yet land on DIFFERENT leaves — shallow on step1, deep on
    // step2. The flat nav-service08 fixture in HistoryTargetTest cannot express this divergence because
    // onboarding's immediate child there is a leaf screen with no deeper state to drop or keep.

    should("shallow and deep coincide when the flow is already at its default at exit") {
      val sut = newService()
      sut.collectTransitions().test {
        // exit onboarding while wizard is still at its DEFAULT (step1), so nothing deeper was drilled
        awaitItem().active shouldBe "app.onboarding.wizard.step1"

        sut.sendEvent(TestEvent("toLogin"))
        awaitItem().active shouldBe "app.login.credentials"

        // both restorations agree on step1 when the recorded leaf IS the default
        sut.sendEvent(TestEvent("histShallow"))
        awaitItem().active shouldBe "app.onboarding.wizard.step1"

        sut.sendEvent(TestEvent("toLogin"))
        awaitItem().active shouldBe "app.login.credentials"

        sut.sendEvent(TestEvent("histDeep"))
        awaitItem().active shouldBe "app.onboarding.wizard.step1"
      }
    }
  })
