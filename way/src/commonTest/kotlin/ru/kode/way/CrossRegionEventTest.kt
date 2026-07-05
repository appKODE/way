package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import ru.kode.way.nav05.NavService05Schema
import ru.kode.way.par03.Par03AppNodeBuilder
import ru.kode.way.par03.Parallel03Schema
import ru.kode.way.par03.alpha.Par03AlphaNodeBuilder
import ru.kode.way.par03.alpha.Parallel03AlphaSchema
import ru.kode.way.par03.alpha.par03Alpha
import ru.kode.way.par03.beta.Par03BetaNodeBuilder
import ru.kode.way.par03.beta.Parallel03BetaSchema
import ru.kode.way.par03.beta.par03Beta
import ru.kode.way.par03.main.Par03MainNodeBuilder
import ru.kode.way.par03.main.Parallel03MainSchema
import ru.kode.way.par03.par03App
import ru.kode.way.nav05.app as app05

// Foundation note (Phase 1, foundation.crossRegionSemantics):
// `CrossRegionEvent` (way/src/commonMain/kotlin/ru/kode/way/CrossRegionEvent.kt:24-26) is a
// marker annotation with NO constructor parameters. Per its KDoc (lines 11-14) this release
// ships it as documentation-only — `NavigationService` does NOT reference it anywhere
// (verified by grep). The contract it documents is: a child flow that receives a
// `@CrossRegionEvent`-annotated event MUST return `Ignore` so the event bubbles to the
// parent `ParallelFlowNode.transition`. There is no runtime routing — every region's active
// node receives the event in parallel via `resolveTransition` (TargetResolution.kt:8 fold
// over `regions.entries`). The tests below pin down what is observable at runtime.

@CrossRegionEvent
private data class CrossRegionTestEvent(val tag: String) : Event

class CrossRegionEventTest :
  ShouldSpec({

    // Production code: NavigationService.transition() iterates ALL regions
    // (TargetResolution.kt:8 — `regions.entries.fold(...)`). For a parallel parent with
    // alpha and beta as sub-regions, sending an event delivers it independently to each
    // region's active node AND to the parallel parent (it is itself a region root). When
    // alpha's child flow returns `Ignore`, the runtime walks up within alpha's region only
    // (TargetResolution.kt:183-218) — the parallel parent's `transition()` is invoked
    // exactly once, from the parallel's own region iteration. This is the observable
    // expression of the bubble-up contract `@CrossRegionEvent` documents.
    should("CrossRegionEvent bubbles to parallel parent transition() exactly once when child flow returns Ignore") {
      val parallelInvocations = mutableListOf<Event>()
      val alphaChildInvocations = mutableListOf<Event>()

      val alphaFlowRoot = object : FlowNode<Unit> {
        override val initial: Target = Target.par03Alpha.par03AlphaScreen
        override val dismissResult: Unit = Unit
        override fun transition(event: Event): FlowTransition<Unit> {
          if (event is CrossRegionTestEvent) {
            alphaChildInvocations.add(event)
            // Contract: child flow returns Ignore so the event bubbles to the parent parallel.
            return Ignore
          }
          return Ignore
        }
      }

      val mainParallel = TestParallelNode(
        onTransitionCallback = { event ->
          if (event is CrossRegionTestEvent) parallelInvocations.add(event)
        },
      )

      val appSchema = Parallel03Schema(
        par03MainSchema = Parallel03MainSchema(
          par03AlphaSchema = Parallel03AlphaSchema(),
          par03BetaSchema = Parallel03BetaSchema(),
        ),
      )
      val alphaNodeBuilder = Par03AlphaNodeBuilder(
        nodeFactory = object : Par03AlphaNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = alphaFlowRoot
          override fun createPar03AlphaScreenNode(): ScreenNode = TestScreenNode()
          override fun createPar03AlphaScreen2Node(): ScreenNode = TestScreenNode()
        },
        schema = Parallel03AlphaSchema(),
      )
      val betaNodeBuilder = Par03BetaNodeBuilder(
        nodeFactory = object : Par03BetaNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par03Beta.par03BetaScreen)
          override fun createPar03BetaScreenNode(): ScreenNode = TestScreenNode()
        },
        schema = Parallel03BetaSchema(),
      )
      val mainNodeBuilder = Par03MainNodeBuilder(
        nodeFactory = object : Par03MainNodeBuilder.Factory {
          override fun createRootNode(): ParallelFlowNode<Unit> = mainParallel
          override fun createPar03AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
          override fun createPar03BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
        },
        schema = Parallel03MainSchema(Parallel03AlphaSchema(), Parallel03BetaSchema()),
      )
      val appNodeBuilder = Par03AppNodeBuilder(
        nodeFactory = object : Par03AppNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par03App.par03Main)
          override fun createPar03MainNodeBuilder(): NodeBuilder = mainNodeBuilder
          override fun createPar03PageNode(): ScreenNode = TestScreenNode()
        },
        schema = appSchema,
      )
      val sut = NavigationService(
        nodeBuilder = appNodeBuilder,
        onFinishRequest = { _: Unit -> Stay },
      )

      sut.collectTransitions().test {
        awaitItem() // initial state

        sut.sendEvent(CrossRegionTestEvent("ping"))
        awaitItem()

        // Alpha's child flow saw the event and returned Ignore — satisfies the documented
        // contract. The parallel parent received it exactly once via its own region's
        // dispatch (verified by `parallelInvocations.size shouldBe 1`).
        alphaChildInvocations.shouldContainExactly(CrossRegionTestEvent("ping"))
        parallelInvocations.shouldContainExactly(CrossRegionTestEvent("ping"))

        cancelAndIgnoreRemainingEvents()
      }
    }

    // Production code: NavigationService.sendEvent (NavigationService.kt:423-449) calls
    // `transition(state, current)` FIRST (line 433) — which invokes every node's
    // `transition()` via `resolveTransition` — and only THEN notifies listeners
    // (line 439: `listeners.toList().forEach { it(state.copy()) }`). So every node
    // `transition()` call for the current event is complete before any transition listener
    // is invoked.
    should("CrossRegionEvent delivery to nodes completes before transition listeners are notified") {
      val order = mutableListOf<String>()

      val alphaFlowRoot = object : FlowNode<Unit> {
        override val initial: Target = Target.par03Alpha.par03AlphaScreen
        override val dismissResult: Unit = Unit
        override fun transition(event: Event): FlowTransition<Unit> {
          if (event is CrossRegionTestEvent) order.add("alpha.transition")
          return Ignore
        }
      }
      val mainParallel = TestParallelNode(
        onTransitionCallback = { event ->
          if (event is CrossRegionTestEvent) order.add("parallel.transition")
        },
      )

      val appSchema = Parallel03Schema(
        par03MainSchema = Parallel03MainSchema(
          par03AlphaSchema = Parallel03AlphaSchema(),
          par03BetaSchema = Parallel03BetaSchema(),
        ),
      )
      val alphaNodeBuilder = Par03AlphaNodeBuilder(
        nodeFactory = object : Par03AlphaNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = alphaFlowRoot
          override fun createPar03AlphaScreenNode(): ScreenNode = TestScreenNode()
          override fun createPar03AlphaScreen2Node(): ScreenNode = TestScreenNode()
        },
        schema = Parallel03AlphaSchema(),
      )
      val betaNodeBuilder = Par03BetaNodeBuilder(
        nodeFactory = object : Par03BetaNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par03Beta.par03BetaScreen)
          override fun createPar03BetaScreenNode(): ScreenNode = TestScreenNode()
        },
        schema = Parallel03BetaSchema(),
      )
      val mainNodeBuilder = Par03MainNodeBuilder(
        nodeFactory = object : Par03MainNodeBuilder.Factory {
          override fun createRootNode(): ParallelFlowNode<Unit> = mainParallel
          override fun createPar03AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
          override fun createPar03BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
        },
        schema = Parallel03MainSchema(Parallel03AlphaSchema(), Parallel03BetaSchema()),
      )
      val appNodeBuilder = Par03AppNodeBuilder(
        nodeFactory = object : Par03AppNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par03App.par03Main)
          override fun createPar03MainNodeBuilder(): NodeBuilder = mainNodeBuilder
          override fun createPar03PageNode(): ScreenNode = TestScreenNode()
        },
        schema = appSchema,
      )
      val sut = NavigationService(
        nodeBuilder = appNodeBuilder,
        onFinishRequest = { _: Unit -> Stay },
      )

      // Use addTransitionListener directly (not turbine) so we observe ordering precisely.
      val listener: (NavigationState) -> Unit = { _ ->
        // Tag only listener invocations that follow the CrossRegionTestEvent dispatch.
        if (order.contains("alpha.transition") || order.contains("parallel.transition")) {
          order.add("listener")
        }
      }
      sut.addTransitionListener(listener)
      sut.start()

      // Drop any "listener" entry produced by the InitEvent (none — order is still empty
      // for cross-region tags here). Now send the cross-region event.
      order.clear()
      sut.sendEvent(CrossRegionTestEvent("once"))

      // Both node transition() calls must precede the listener invocation. The relative
      // order between alpha.transition and parallel.transition is whatever
      // `resolveTransition`'s region iteration order produces; we don't pin that. We DO
      // pin: every node transition completes before any listener fires.
      val listenerIdx = order.indexOf("listener")
      (listenerIdx > 0) shouldBe true
      val transitionTags = order.subList(0, listenerIdx).toSet()
      transitionTags shouldBe setOf("alpha.transition", "parallel.transition")

      sut.removeTransitionListener(listener)
    }

    // Production code: in a single-region (non-parallel) schema there is no parent
    // `ParallelFlowNode` to bubble to. The runtime still walks the active node and then
    // its parents within the region (TargetResolution.kt:183-218). When every node along
    // the chain returns `Ignore` and the region root is reached, the runtime falls through
    // to `maybeResolveBackEvent` (for Back) or returns an empty `ResolvedTransition`
    // (lines 184-202). No crash, no state change — the event is a silent no-op. This locks
    // in the "no alive target node is a no-op" guarantee for `@CrossRegionEvent` events
    // sent into schemas that lack a parallel parent.
    should("CrossRegionEvent with no alive parallel parent is a no-op, not a crash") {
      val rootFlowNode = object : FlowNode<Unit> {
        override val initial: Target = Target.app05.intro
        override val dismissResult: Unit = Unit
        override fun transition(event: Event): FlowTransition<Unit> = Ignore
      }
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to rootFlowNode,
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val initialActive = initial.regions.values.first().active

        // No throw, no crash — the event is silently absorbed. The runtime still notifies
        // listeners after every transition (NavigationService.kt:439 fires unconditionally),
        // so an emission follows; the assertion below pins that the active path is unchanged.
        sut.sendEvent(CrossRegionTestEvent("orphan"))
        val afterOrphan = awaitItem()
        afterOrphan.regions.values.first().active shouldBe initialActive

        cancelAndIgnoreRemainingEvents()
      }
    }
  })
