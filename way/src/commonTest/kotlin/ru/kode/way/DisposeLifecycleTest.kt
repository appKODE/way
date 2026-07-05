package ru.kode.way

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import ru.kode.way.nav01.NavService01Schema
import ru.kode.way.nav05.NavService05Schema
import ru.kode.way.par04.Par04AppNodeBuilder
import ru.kode.way.par04.Parallel04Schema
import ru.kode.way.par04.alpha.Par04AlphaNodeBuilder
import ru.kode.way.par04.alpha.Parallel04AlphaSchema
import ru.kode.way.par04.beta.Par04BetaNodeBuilder
import ru.kode.way.par04.beta.Parallel04BetaSchema
import ru.kode.way.par04.beta.par04Beta
import ru.kode.way.par04.innera.Par04InnerANodeBuilder
import ru.kode.way.par04.innera.Parallel04InnerASchema
import ru.kode.way.par04.innera.par04InnerA
import ru.kode.way.par04.innerb.Par04InnerBNodeBuilder
import ru.kode.way.par04.innerb.Parallel04InnerBSchema
import ru.kode.way.par04.innerb.par04InnerB
import ru.kode.way.par04.main.Par04MainNodeBuilder
import ru.kode.way.par04.main.Parallel04MainSchema
import ru.kode.way.par04.par04App
import ru.kode.way.nav01.app as app01
import ru.kode.way.nav05.app as app05

class DisposeLifecycleTest :
  ShouldSpec({
    should("start() twice throws IllegalStateException") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )
      sut.start()

      shouldThrow<IllegalStateException> {
        sut.start()
      }
    }

    should("sendEvent before start() throws IllegalStateException") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )

      shouldThrow<IllegalStateException> {
        sut.sendEvent(TestEvent("anything"))
      }
    }

    should("dispose() is idempotent — second call no-ops") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )
      sut.start()

      sut.dispose()
      // Second call must not throw and must be a no-op.
      sut.dispose()

      // After dispose, sendEvent is a silent no-op — proves the second dispose() didn't re-arm anything.
      sut.sendEvent(TestEvent("anything"))
    }

    should("after dispose() sendEvent is a silent no-op and listeners are not invoked") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app01.intro,
              transitions = listOf(tr("go", Stay)),
            ),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )

      val deliveries = mutableListOf<String>()
      val listener: (NavigationState) -> Unit = { state -> deliveries.add(state.active) }
      sut.addTransitionListener(listener)
      sut.start() // listener receives "app.intro"
      deliveries shouldBe listOf("app.intro")

      sut.dispose()

      // sendEvent after dispose must NOT throw and must NOT notify the listener.
      sut.sendEvent(TestEvent("go"))
      sut.sendEvent(TestEvent("another"))

      deliveries shouldBe listOf("app.intro")
    }

    should("cleanDispose() on a never-started service is a safe no-op") {
      val disposed = mutableListOf<String>()
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app01.intro,
              onDisposeImpl = { disposed.add("app") },
            ),
            "app.intro" to TestScreenNode(onDisposeImpl = { disposed.add("app.intro") }),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )

      // Service was never started — there are no alive nodes, so onDispose must NOT fire,
      // and the call must not throw.
      sut.cleanDispose()

      disposed shouldBe emptyList()
      // Subsequent sendEvent must be a silent no-op (dispose() was invoked from inside cleanDispose).
      sut.sendEvent(TestEvent("anything"))
    }

    should("cleanDispose() calls onDispose leaf-to-root with sub-regions first") {
      val disposed = mutableListOf<String>()

      val innerASchema = Parallel04InnerASchema()
      val innerBSchema = Parallel04InnerBSchema()
      val betaSchema = Parallel04BetaSchema()
      val alphaSchema = Parallel04AlphaSchema(par04InnerASchema = innerASchema, par04InnerBSchema = innerBSchema)
      val mainSchema = Parallel04MainSchema(par04AlphaSchema = alphaSchema, par04BetaSchema = betaSchema)
      val appSchema = Parallel04Schema(par04MainSchema = mainSchema)

      val innerANodeBuilder = Par04InnerANodeBuilder(
        nodeFactory = object : Par04InnerANodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par04InnerA.par04InnerAScreen2,
            onDisposeImpl = { disposed.add("par04InnerA") },
          )
          override fun createPar04InnerAScreen1Node(): ScreenNode = TestScreenNode()
          override fun createPar04InnerAScreen2Node(): ScreenNode = TestScreenNode(
            onDisposeImpl = { disposed.add("par04InnerAScreen2") },
          )
        },
        schema = innerASchema,
      )
      val innerBNodeBuilder = Par04InnerBNodeBuilder(
        nodeFactory = object : Par04InnerBNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par04InnerB.par04InnerBScreen,
            onDisposeImpl = { disposed.add("par04InnerB") },
          )
          override fun createPar04InnerBScreenNode(): ScreenNode = TestScreenNode(
            onDisposeImpl = { disposed.add("par04InnerBScreen") },
          )
        },
        schema = innerBSchema,
      )
      val alphaNodeBuilder = Par04AlphaNodeBuilder(
        nodeFactory = object : Par04AlphaNodeBuilder.Factory {
          override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
            onDisposeImpl = { disposed.add("par04Alpha") },
          )
          override fun createPar04InnerANodeBuilder(): NodeBuilder = innerANodeBuilder
          override fun createPar04InnerBNodeBuilder(): NodeBuilder = innerBNodeBuilder
        },
        schema = alphaSchema,
      )
      val betaNodeBuilder = Par04BetaNodeBuilder(
        nodeFactory = object : Par04BetaNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par04Beta.par04BetaScreen,
            onDisposeImpl = { disposed.add("par04Beta") },
          )
          override fun createPar04BetaScreenNode(): ScreenNode = TestScreenNode(
            onDisposeImpl = { disposed.add("par04BetaScreen") },
          )
        },
        schema = betaSchema,
      )
      val mainNodeBuilder = Par04MainNodeBuilder(
        nodeFactory = object : Par04MainNodeBuilder.Factory {
          override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
            onDisposeImpl = { disposed.add("par04Main") },
          )
          override fun createPar04AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
          override fun createPar04BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
        },
        schema = mainSchema,
      )
      val appNodeBuilder = Par04AppNodeBuilder(
        nodeFactory = object : Par04AppNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par04App.par04Main,
            onDisposeImpl = { disposed.add("par04App") },
          )
          override fun createPar04MainNodeBuilder(): NodeBuilder = mainNodeBuilder
        },
        schema = appSchema,
      )
      val sut: NavigationService<Unit> = NavigationService(
        nodeBuilder = appNodeBuilder,
        onFinishRequest = { Ignore },
      )
      sut.start()

      sut.cleanDispose()

      // Within each region: leaf screen disposed before its owning flow root.
      disposed.indexOf("par04InnerAScreen2") shouldBeLessThan disposed.indexOf("par04InnerA")
      disposed.indexOf("par04InnerBScreen") shouldBeLessThan disposed.indexOf("par04InnerB")
      disposed.indexOf("par04BetaScreen") shouldBeLessThan disposed.indexOf("par04Beta")

      // Innermost sub-regions (par04InnerA / par04InnerB) disposed before their parent parallel (par04Alpha).
      disposed.indexOf("par04InnerA") shouldBeLessThan disposed.indexOf("par04Alpha")
      disposed.indexOf("par04InnerB") shouldBeLessThan disposed.indexOf("par04Alpha")

      // Mid-level sub-regions (par04Alpha / par04Beta) disposed before the top parallel (par04Main).
      disposed.indexOf("par04Alpha") shouldBeLessThan disposed.indexOf("par04Main")
      disposed.indexOf("par04Beta") shouldBeLessThan disposed.indexOf("par04Main")

      // The root parallel disposed before the outermost app flow.
      disposed.indexOf("par04Main") shouldBeLessThan disposed.indexOf("par04App")
    }

    should("cleanDispose() invoked from inside a transition listener throws IllegalStateException") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )

      // The listener is added BEFORE start(); start() drives the InitEvent through sendEvent,
      // setting isDispatching = true. cleanDispose() must reject the call with IllegalStateException
      // from its `check(!isDispatching)` guard. The listener swallows it via runCatching, so
      // start() itself completes normally — the contract under test is on cleanDispose, not start.
      var thrown: Throwable? = null
      sut.addTransitionListener { _ ->
        if (thrown == null) {
          thrown = runCatching { sut.cleanDispose() }.exceptionOrNull()
        }
      }

      sut.start()
      (thrown is IllegalStateException) shouldBe true
    }

    should("throwing Node.onDispose does not stop the cascade — every other node still receives onDispose") {
      val disposed = mutableListOf<String>()
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              onDisposeImpl = { disposed.add("app") },
              transitions = listOf(tr("go", NavigateTo(Target.app05.main))),
            ),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(
              onDisposeImpl = {
                disposed.add("app.main")
                error("app.main onDispose intentionally throws")
              },
            ),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      sut.start()
      sut.sendEvent(TestEvent("go"))

      // After navigating, alive nodes are [app, app.main]; cleanDispose disposes leaf-to-root.
      // app.main throws inside onDispose — runCatching around node.onDispose() must swallow it,
      // and the cascade must still reach app.
      sut.cleanDispose()

      disposed shouldContainInOrder listOf("app.main", "app")
    }

    should("dispose() invoked from inside a transition listener throws IllegalStateException") {
      // Mirrors the existing cleanDispose-from-listener contract: calling dispose() while
      // isDispatching == true must throw immediately. Without the guard, dispose() clears
      // _regions / _intermediateParallels / _payloads / listeners mid-iteration of
      // sendEvent's `listeners.toList().forEach`, leaving later listeners with a corrupted
      // NavigationState snapshot.
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )

      var thrown: Throwable? = null
      sut.addTransitionListener { _ ->
        if (thrown == null) {
          thrown = runCatching { sut.dispose() }.exceptionOrNull()
        }
      }

      sut.start()
      (thrown is IllegalStateException) shouldBe true
    }

    should("addTransitionListener after dispose() does not register the listener (no leak)") {
      // The leak fix asserts that addTransitionListener returns early when isDisposed,
      // so the listener instance is not retained in the internal `listeners` ArrayList.
      // Without the fix the listener would stay in the list forever and never be invoked
      // (sendEvent is a no-op after dispose), leaking the closure and everything it captures.
      //
      // Assert via reflection on the private `listeners` field so the test fails when the
      // listener IS appended post-dispose, even though sendEvent post-dispose hides the bug
      // through any observable callback channel.
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )
      sut.start()
      sut.dispose()

      val leakyListener: (NavigationState) -> Unit = { _ -> }
      sut.addTransitionListener(leakyListener)

      val listenersField = NavigationService::class.java.getDeclaredField("listeners").apply { isAccessible = true }

      @Suppress("UNCHECKED_CAST")
      val internalListeners = listenersField.get(sut) as List<(NavigationState) -> Unit>
      internalListeners shouldBe emptyList()
    }

    should("addServiceExtensionPoint after dispose() does not register the extension point (no leak)") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )
      sut.start()
      sut.dispose()

      val leakyPoint = TestServiceExtensionPoint<Int>(
        preTransition = { _, _, _ -> },
        postTransition = { _, _, _ -> },
      )
      sut.addServiceExtensionPoint(leakyPoint)

      val field = NavigationService::class.java.getDeclaredField("serviceExtensionPoints")
        .apply { isAccessible = true }

      @Suppress("UNCHECKED_CAST")
      val internal = field.get(sut) as List<ServiceExtensionPoint<Int>>
      internal shouldBe emptyList()
    }

    should("addNodeExtensionPoint after dispose() does not register the extension point (no leak)") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )
      sut.start()
      sut.dispose()

      val leakyPoint = TestNodeExtensionPoint()
      sut.addNodeExtensionPoint(leakyPoint)

      // State's _nodeExtensionPoints was cleared by dispose(); a guarded add must NOT
      // re-populate it from a disposed state.
      val stateField = NavigationService::class.java.getDeclaredField("state")
        .apply { isAccessible = true }
      val navState = stateField.get(sut) as NavigationState
      navState._nodeExtensionPoints shouldBe emptyList()
    }

    should("removeTransitionListener after dispose() is a safe no-op") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )
      val listener: (NavigationState) -> Unit = { _ -> }
      sut.addTransitionListener(listener)
      sut.start()
      sut.dispose()

      // Must not throw.
      sut.removeTransitionListener(listener)
    }

    should(
      "addTransitionListener after start() that throws inside immediate-invoke is auto-removed and exception propagates",
    ) {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app01.intro,
              transitions = listOf(tr("go", Stay)),
            ),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )
      sut.start()

      val throwingListener: (NavigationState) -> Unit = { _ ->
        throw RuntimeException("immediate-invoke listener error")
      }
      val ex = runCatching { sut.addTransitionListener(throwingListener) }.exceptionOrNull()
      // Exception must propagate to the caller.
      (ex is RuntimeException) shouldBe true
      ex?.message shouldBe "immediate-invoke listener error"

      // The throwing listener must have been auto-removed: a subsequent sendEvent must NOT
      // re-invoke it (otherwise the throw would propagate out of sendEvent again).
      // The fresh listener is added AFTER start(), so it receives one immediate-invoke delivery
      // for the current state plus one delivery for the Stay transition driven by "go".
      val deliveries = mutableListOf<String>()
      sut.addTransitionListener { state -> deliveries.add(state.active) }
      sut.sendEvent(TestEvent("go")) // must not re-throw
      deliveries shouldBe listOf("app.intro", "app.intro")
    }
  })
