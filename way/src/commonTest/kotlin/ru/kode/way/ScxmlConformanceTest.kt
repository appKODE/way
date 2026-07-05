package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import ru.kode.way.acmetabs.AcmeAppFlowNodeBuilder
import ru.kode.way.acmetabs.AcmeAuthFlowNodeBuilder
import ru.kode.way.acmetabs.AcmeAuthFlowSchema
import ru.kode.way.acmetabs.AcmeExploreTabNodeBuilder
import ru.kode.way.acmetabs.AcmeExploreTabSchema
import ru.kode.way.acmetabs.AcmeHomeTabNodeBuilder
import ru.kode.way.acmetabs.AcmeHomeTabSchema
import ru.kode.way.acmetabs.AcmeMainFlowNodeBuilder
import ru.kode.way.acmetabs.AcmeMainFlowSchema
import ru.kode.way.acmetabs.AcmeTabsFlowNodeBuilder
import ru.kode.way.acmetabs.AcmeTabsFlowSchema
import ru.kode.way.acmetabs.ParallelTestAcmeTabsSchema
import ru.kode.way.acmetabs.acmeAuthFlow
import ru.kode.way.acmetabs.acmeExploreTab
import ru.kode.way.acmetabs.acmeHomeTab
import ru.kode.way.nav08.NavService08Schema
import ru.kode.way.nav08.app as app08
import ru.kode.way.nav08.login as login08
import ru.kode.way.nav08.onboarding as onboarding08

/**
 * End-to-end conformance suite for the load-bearing guarantees of the W3C SCXML "Algorithm for
 * SCXML Interpretation" (Recommendation, Appendix B) that Way's runtime now implements. Every test
 * drives a *running* [NavigationService] through the existing `collectTransitions().test { }`
 * Turbine idiom and asserts observable configuration (`state.regions` + each region's
 * `.active`/`.alive`). Where useful the observed configuration is cross-checked against the
 * canonical schema-static building blocks in `StatechartAlgorithm.kt` (`findLCCA`,
 * `computeExitSet`, `getTransitionDomain`).
 *
 * Terminology map (SCXML -> Way): compound state -> Flow, <parallel> -> ParallelFlow, atomic state
 * -> Screen, configuration -> the alive absolute Paths across all regions, LCCA -> nearest common
 * Flow ancestor (parallels excluded).
 *
 * Fixtures reused (no new `.dot` files):
 * - `nav-service08` (`NavService08Schema`): `app` flow with sibling child flows `onboarding`
 *   (screens `intro` (initial) + `page1`) and `login` (screen `credentials`). Same fixture as
 *   `HistoryTargetTest`.
 * - `parallel-test-acme-tabs` (`ParallelTestAcmeTabsSchema`): `acmeAppFlow` (parallel root) with
 *   regions `acmeMainFlow` (-> nested `acmeTabsFlow` parallel -> `acmeHomeTab` + `acmeExploreTab`)
 *   and `acmeAuthFlow`. Same tree as the acme-tabs tests in `ParallelNodeTest`.
 */
class ScxmlConformanceTest :
  ShouldSpec({

    val nav08Schema = NavService08Schema()
    val nodeTypeOf08: (Path) -> Schema.NodeType = { findNodeType(nav08Schema, it) }

    // Absolute paths built from the schema so their Segment ids (with @file disambiguators) match
    // the runtime region/node paths — a hand-typed Path("app", "onboarding") would fail the
    // region-root check (see HistoryTargetTest).
    val onboardingPath = AbsoluteTarget(nav08Schema.rootSegment, Target.app08.onboarding).path
    val loginPath = AbsoluteTarget(nav08Schema.rootSegment, Target.app08.login).path
    // Deep atomic target `app.onboarding.page1`: onboarding is an intermediate compound ancestor of
    // page1 that the caller does not separately enter.
    val onboardingPage1 = AbsoluteTarget(nav08Schema.rootSegment, Target.app08.onboarding, Target.onboarding08.page1)
    val loginCredentialsPath =
      AbsoluteTarget(nav08Schema.rootSegment, Target.app08.login, Target.login08.credentials).path

    // nav08 service whose `app` starts in the `login` subtree (NOT onboarding). Navigating to the
    // deep `onboarding.page1` target therefore has to auto-enter the orthogonal `onboarding`
    // compound as an intermediate. `entryLog`/`exitLog` capture onEntry/onExit firing order.
    fun newDeepService(entryLog: MutableList<String>, exitLog: MutableList<String>): NavigationService<Unit> =
      NavigationService(
        TestNodeBuilder(
          nav08Schema,
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app08.login,
              onEntryImpl = { entryLog.add("app") },
              onExitImpl = { exitLog.add("app") },
              transitions = listOf(
                tr("toDeep", onboardingPage1),
              ),
            ),
            "app.login" to TestFlowNode(
              initialTarget = Target.login08.credentials,
              onEntryImpl = { entryLog.add("app.login") },
              onExitImpl = { exitLog.add("app.login") },
            ),
            "app.login.credentials" to TestScreenNode(
              onEntryImpl = { entryLog.add("app.login.credentials") },
              onExitImpl = { exitLog.add("app.login.credentials") },
            ),
            "app.onboarding" to TestFlowNode(
              initialTarget = Target.onboarding08.intro,
              onEntryImpl = { entryLog.add("app.onboarding") },
              onExitImpl = { exitLog.add("app.onboarding") },
            ),
            "app.onboarding.intro" to TestScreenNode(
              onEntryImpl = { entryLog.add("app.onboarding.intro") },
              onExitImpl = { exitLog.add("app.onboarding.intro") },
            ),
            "app.onboarding.page1" to TestScreenNode(
              onEntryImpl = { entryLog.add("app.onboarding.page1") },
              onExitImpl = { exitLog.add("app.onboarding.page1") },
            ),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

    // nav08 service whose `app` starts in `onboarding` — mirrors HistoryTargetTest's fixture so the
    // onboarding flow can be drilled, exited (recording history), and later restored.
    fun newHistoryService(): NavigationService<Unit> = NavigationService(
      TestNodeBuilder(
        nav08Schema,
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

    // ── Guarantee 1 ──────────────────────────────────────────────────────────────────────────────
    should(
      "SCXML computeEntrySet (addAncestorStatesToEnter): a deep target auto-enters every intermediate compound ancestor",
    ) {
      val entryLog = mutableListOf<String>()
      val exitLog = mutableListOf<String>()
      val sut = newDeepService(entryLog, exitLog)

      sut.collectTransitions().test {
        // Start in the login subtree; onboarding is NOT alive yet.
        awaitItem().apply {
          active shouldBe "app.login.credentials"
          alive.none { it == "app.onboarding" } shouldBe true
        }
        entryLog.contains("app.onboarding") shouldBe false

        // ONE navigation to the deep atomic `app.onboarding.page1`. The caller never separately
        // enters the intermediate `onboarding` compound.
        sut.sendEvent(TestEvent("toDeep"))
        awaitItem().apply {
          active shouldBe "app.onboarding.page1"
          // The region's alive configuration now contains every ancestor of the target in
          // root->leaf (document/entry) order — the runtime filled the intermediates.
          alive.shouldContainInOrder("app", "app.onboarding", "app.onboarding.page1")
        }
        // The intermediate compound was materialised (its node onEntry fired) — not merely listed.
        entryLog.contains("app.onboarding") shouldBe true
        // It was auto-entered ABOVE the atomic leaf (addAncestorStatesToEnter is document order:
        // ancestor before descendant).
        entryLog.indexOf("app.onboarding") shouldBeLessThanIndexOf entryLog.indexOf("app.onboarding.page1")

        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── Guarantee 2 ──────────────────────────────────────────────────────────────────────────────
    should("SCXML addDescendantStatesToEnter: entering a <parallel> enters ALL of its regions simultaneously") {
      val sut = buildAcmeConformanceService()

      sut.collectTransitions().test {
        awaitItem().apply {
          // Reaching the acmeTabsFlow parallel (on the way to the initial screen) materialised
          // BOTH tab regions at once, alongside the outer parallel root's own regions.
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
            "acmeMainFlow",
            "acmeAuthFlow",
            "acmeHomeTab",
            "acmeExploreTab",
          )
          // Each orthogonal region carries its OWN active atomic leaf (AND-state semantics).
          activeLeafOf("acmeHomeTab") shouldBe "acmeHomeScreen"
          activeLeafOf("acmeExploreTab") shouldBe "acmeExploreScreen"
          activeLeafOf("acmeAuthFlow") shouldBe "acmeAuthScreen"
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── Guarantee 3 ──────────────────────────────────────────────────────────────────────────────
    should(
      "SCXML transition-domain scoping (LCCA): a transition inside one region leaves orthogonal sibling regions untouched",
    ) {
      val schema = ParallelTestAcmeTabsSchema()
      val nodeTypeOf: (Path) -> Schema.NodeType = { findNodeType(schema, it) }
      val sut = buildAcmeConformanceService(
        homeTabTransitions = listOf(
          TestFlowTransitionSpec(
            eventMatcher = { it is TestEvent && it.name == "openTopUp" },
            transition = NavigateTo(Target.acmeHomeTab.acmeTopUpScreen),
          ),
        ),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val homeLeaf = initial.regionActive("acmeHomeTab")
        val exploreLeaf = initial.regionActive("acmeExploreTab")
        val authActiveBefore = initial.regionActive("acmeAuthFlow")
        val exploreActiveBefore = initial.regionActive("acmeExploreTab")

        // Canonical cross-check: the two tabs' nearest common ancestor is the acmeTabsFlow parallel;
        // SCXML forbids a <parallel> as an LCCA, so the domain lifts to the enclosing compound
        // acmeMainFlow. The orthogonal acmeAuthFlow is NOT a descendant of that domain, so it can
        // never be in any within-tab transition's exit set.
        val lcca = findLCCA(listOf(homeLeaf, exploreLeaf), nodeTypeOf)
        lcca.lastSegment().name shouldBe "acmeMainFlow"
        computeExitSet(lcca, initial.configuration()).any { it == authActiveBefore } shouldBe false

        // Drive ONLY the Home tab to its top-up screen.
        sut.sendEvent(TestEvent("openTopUp"))
        awaitItem().apply {
          activeLeafOf("acmeHomeTab") shouldBe "acmeTopUpScreen"
          // Orthogonal sibling regions are byte-for-byte unchanged.
          regionActive("acmeExploreTab") shouldBe exploreActiveBefore
          regionActive("acmeAuthFlow") shouldBe authActiveBefore
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── Guarantee 4 ──────────────────────────────────────────────────────────────────────────────
    should(
      "SCXML shallow history: HistoryTarget(deep=false) restores the previously-active child, not the default initial",
    ) {
      val sut = newHistoryService()
      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.onboarding.intro"

        // drill to the NON-default child of onboarding, then exit (history recorded here)
        sut.sendEvent(TestEvent("toPage1"))
        awaitItem().active shouldBe "app.onboarding.page1"
        sut.sendEvent(TestEvent("toLogin"))
        awaitItem().active shouldBe "app.login.credentials"

        // shallow history restores page1 (the child active at last exit), NOT the default intro
        sut.sendEvent(TestEvent("histOnboardingShallow"))
        awaitItem().active shouldBe "app.onboarding.page1"

        cancelAndIgnoreRemainingEvents()
      }
    }

    should("SCXML deep history: HistoryTarget(deep=true) restores the recorded atomic leaf") {
      val sut = newHistoryService()
      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.onboarding.intro"

        sut.sendEvent(TestEvent("toPage1"))
        awaitItem().active shouldBe "app.onboarding.page1"
        sut.sendEvent(TestEvent("toLogin"))
        awaitItem().active shouldBe "app.login.credentials"

        sut.sendEvent(TestEvent("histOnboardingDeep"))
        awaitItem().active shouldBe "app.onboarding.page1"

        cancelAndIgnoreRemainingEvents()
      }
    }

    should(
      "SCXML history default: a first-visit HistoryTarget (no recorded history) falls back to the flow's default initial",
    ) {
      val sut = newHistoryService()
      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.onboarding.intro"

        // login flow was never entered → no recorded history → behaves like FlowTarget(login):
        // enters login's default initial (credentials).
        sut.sendEvent(TestEvent("histLoginShallow"))
        awaitItem().active shouldBe "app.login.credentials"

        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── Guarantee 5 ──────────────────────────────────────────────────────────────────────────────
    should(
      "SCXML microstep ordering: exited states fire onExit leaf->root and entered states fire onEntry root->leaf",
    ) {
      val entryLog = mutableListOf<String>()
      val exitLog = mutableListOf<String>()
      val sut = newDeepService(entryLog, exitLog)

      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.login.credentials"
        // Ignore the initial-entry noise; measure only the one transition under test.
        entryLog.clear()
        exitLog.clear()

        // login.credentials -> onboarding.page1. Domain = LCCA = app (unchanged, not re-entered):
        // exit {login.credentials, login}; enter {onboarding, page1}.
        sut.sendEvent(TestEvent("toDeep"))
        awaitItem().active shouldBe "app.onboarding.page1"

        // Exit set fires deepest-first (reverse document order): the leaf screen before its flow.
        exitLog shouldBe listOf("app.login.credentials", "app.login")
        // Entry set fires shallowest-first (document order): the intermediate compound before its leaf.
        entryLog shouldBe listOf("app.onboarding", "app.onboarding.page1")

        // Canonical cross-check: computeExitSet over the pre-transition configuration, scoped to the
        // transition domain (app), reproduces exactly the observed leaf-first onExit order.
        val domain = getTransitionDomain(
          source = loginCredentialsPath,
          targets = listOf(onboardingPage1.path),
          isInternal = false,
          nodeTypeOf = nodeTypeOf08,
        )
        domain shouldBe Path(nav08Schema.rootSegment)
        val expectedExit = computeExitSet(
          domain = Path(nav08Schema.rootSegment),
          configuration = listOf(loginCredentialsPath, loginPath),
        ).map { it.toString() }
        expectedExit shouldBe exitLog

        cancelAndIgnoreRemainingEvents()
      }
    }
  })

// Last segment of the active path of the region whose root segment name is [regionName].
private fun NavigationState.activeLeafOf(regionName: String): String = regionActive(regionName).lastSegment().name

// Active absolute Path of the region whose root segment name is [regionName].
private fun NavigationState.regionActive(regionName: String): Path =
  regions.entries.first { it.key.path.lastSegment().name == regionName }.value.active

// The SCXML configuration: every alive absolute Path across all regions.
private fun NavigationState.configuration(): List<Path> = regions.values.flatMap { it.alive }

private infix fun Int.shouldBeLessThanIndexOf(other: Int) {
  (this < other) shouldBe true
}

// Reconstruction of the acme-tabs service (ParallelNodeTest.buildAcmeTabsService is file-private).
// Layout: `parallel acmeAppFlow -> {acmeMainFlow -> parallel acmeTabsFlow -> {acmeHomeTab,
// acmeExploreTab}, acmeAuthFlow}`. Reuses the generated *Schema/*NodeBuilder classes; adds no
// `.dot` fixtures.
private fun buildAcmeConformanceService(
  homeTabTransitions: List<TestFlowTransitionSpec> = emptyList(),
  exploreTabTransitions: List<TestFlowTransitionSpec> = emptyList(),
): NavigationService<Unit> {
  val homeTabNodeBuilder = AcmeHomeTabNodeBuilder(
    nodeFactory = object : AcmeHomeTabNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.acmeHomeTab.acmeHomeScreen,
        transitions = homeTabTransitions,
      )
      override fun createAcmeHomeScreenNode(): ScreenNode = TestScreenNode()
      override fun createAcmeTopUpScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = AcmeHomeTabSchema(),
  )
  val exploreTabNodeBuilder = AcmeExploreTabNodeBuilder(
    nodeFactory = object : AcmeExploreTabNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.acmeExploreTab.acmeExploreScreen,
        transitions = exploreTabTransitions,
      )
      override fun createAcmeExploreScreenNode(): ScreenNode = TestScreenNode()
      override fun createAcmeExploreDetailScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = AcmeExploreTabSchema(),
  )
  val tabsFlowNodeBuilder = AcmeTabsFlowNodeBuilder(
    nodeFactory = object : AcmeTabsFlowNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
      override fun createAcmeHomeTabNodeBuilder(): NodeBuilder = homeTabNodeBuilder
      override fun createAcmeExploreTabNodeBuilder(): NodeBuilder = exploreTabNodeBuilder
    },
    schema = AcmeTabsFlowSchema(),
  )
  val mainFlowNodeBuilder = AcmeMainFlowNodeBuilder(
    nodeFactory = object : AcmeMainFlowNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.acmeHomeTab.acmeHomeScreen)
      override fun createAcmeTabsFlowNodeBuilder(): NodeBuilder = tabsFlowNodeBuilder
    },
    schema = AcmeMainFlowSchema(),
  )
  val authFlowNodeBuilder = AcmeAuthFlowNodeBuilder(
    nodeFactory = object : AcmeAuthFlowNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.acmeAuthFlow.acmeAuthScreen)
      override fun createAcmeAuthScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = AcmeAuthFlowSchema(),
  )
  val appFlowNodeBuilder = AcmeAppFlowNodeBuilder(
    nodeFactory = object : AcmeAppFlowNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
      override fun createAcmeMainFlowNodeBuilder(): NodeBuilder = mainFlowNodeBuilder
      override fun createAcmeAuthFlowNodeBuilder(): NodeBuilder = authFlowNodeBuilder
      override fun createAcmeTabsFlowNodeBuilder(): NodeBuilder = tabsFlowNodeBuilder
    },
    schema = ParallelTestAcmeTabsSchema(),
  )
  return NavigationService(nodeBuilder = appFlowNodeBuilder, onFinishRequest = { Ignore })
}
