package ru.kode.way

import app.cash.turbine.test
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import ru.kode.way.extension.node.hook.BaseFlowNode
import ru.kode.way.extension.node.hook.BaseScreenNode
import ru.kode.way.extension.node.hook.FlowNodeHook
import ru.kode.way.extension.node.hook.NodeHooksSupportExtensionPoint
import ru.kode.way.extension.node.hook.ScreenNodeHook
import ru.kode.way.nav05.NavService05Schema
import ru.kode.way.nav07.NavService07Schema
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
import ru.kode.way.nav07.AppChildFinishRequest as Nav07AppChildFinishRequest
import ru.kode.way.nav07.app as app07
import ru.kode.way.nav07.login as login07
import ru.kode.way.nav07.onboarding as onboarding07

class NodeHooksTest :
  ShouldSpec({
    should("FlowNodeHook fires onPreEntry, onPreTransition, onPostTransition, onPostExit in correct order") {
      val callbackOrder = mutableListOf<String>()

      val hook = object : FlowNodeHook<Unit> {
        override fun onPreEntry() {
          callbackOrder.add("onPreEntry")
        }
        override fun onPostEntry() {
          callbackOrder.add("onPostEntry")
        }
        override fun onPreTransition(event: Event) {
          callbackOrder.add("onPreTransition")
        }
        override fun onPostTransition(event: Event, transition: FlowTransition<Unit>) {
          callbackOrder.add("onPostTransition")
        }
        override fun onPreExit() {
          callbackOrder.add("onPreExit")
        }
        override fun onPostExit() {
          callbackOrder.add("onPostExit")
        }
      }

      val rootFlowNode = object : BaseFlowNode<Unit>() {
        override val initial: Target = Target.app05.intro
        override val dismissResult: Unit = Unit

        override fun transition(event: Event): FlowTransition<Unit> = when {
          event is TestEvent && event.name == "A" -> NavigateTo(Target.app05.main)
          else -> super.transition(event)
        }
      }
      rootFlowNode.addHook(hook)

      val nodeHookExtensionPoint = NodeHooksSupportExtensionPoint()

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
      sut.addNodeExtensionPoint(nodeHookExtensionPoint)

      sut.collectTransitions().test {
        awaitItem() // initial state: app.intro

        // onEntry fired during init
        callbackOrder.shouldContainExactly("onPreEntry", "onPostEntry")
        callbackOrder.clear()

        sut.sendEvent(TestEvent("A"))
        awaitItem() // navigates to app.main

        // A transition event: onPreTransition, onPostTransition fired; no exit yet
        callbackOrder.shouldContainExactly("onPreTransition", "onPostTransition")

        cancelAndIgnoreRemainingEvents()
      }

      // After cancel: flow node is exited
      // (onPreExit/onPostExit fire during cleanup — turbine cancel triggers awaitClose which
      //  removes the listener but does NOT send an exit event, so we test exit via dispose)
    }

    should("FlowNodeHook onPreExit and onPostExit fire when flow node exits via Back") {
      val callbackOrder = mutableListOf<String>()

      val hook = object : FlowNodeHook<String> {
        override fun onPreEntry() {}
        override fun onPostEntry() {}
        override fun onPreTransition(event: Event) {}
        override fun onPostTransition(event: Event, transition: FlowTransition<String>) {}
        override fun onPreExit() {
          callbackOrder.add("onPreExit")
        }
        override fun onPostExit() {
          callbackOrder.add("onPostExit")
        }
      }

      val loginFlowNode = object : BaseFlowNode<String>() {
        override val initial: Target = Target.login07.credentials
        override val dismissResult: String = ""
      }
      loginFlowNode.addHook(hook)

      val sut = NavigationService(
        TestNodeBuilder(
          NavService07Schema(),
          mapOf(
            "app" to object : FlowNode<Double> {
              override val initial = Target.app07.login
              override val dismissResult = 0.0
              override fun transition(event: Event): FlowTransition<Double> = when (event) {
                is Nav07AppChildFinishRequest.Login -> NavigateTo(Target.app07.onboarding)
                is Nav07AppChildFinishRequest.Onboarding -> Ignore
                else -> Ignore
              }
            },
            "app.login" to loginFlowNode,
            "app.login.credentials" to TestScreenNode(),
            "app.onboarding" to TestFlowNode(initialTarget = Target.onboarding07.intro),
            "app.onboarding.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Double -> Stay },
      )
      sut.addNodeExtensionPoint(NodeHooksSupportExtensionPoint())

      sut.collectTransitions().test {
        awaitItem() // initial: app.login.credentials; hook entry callbacks fired
        callbackOrder.clear() // ignore entry events — only testing exit

        sut.sendEvent(Event.Back)
        // Back from credentials → Finish(login) enqueued; state stays at credentials
        awaitItem()
        // Enqueued Login finish → app navigates to onboarding; login flow exits here
        awaitItem()

        callbackOrder.shouldContainExactly("onPreExit", "onPostExit")
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("FlowNodeHook fires for sub-region flow node in a parallel") {
      val callbackOrder = mutableListOf<String>()

      val hook = object : FlowNodeHook<Unit> {
        override fun onPreEntry() {
          callbackOrder.add("onPreEntry")
        }
        override fun onPostEntry() {
          callbackOrder.add("onPostEntry")
        }
        override fun onPreTransition(event: Event) {}
        override fun onPostTransition(event: Event, transition: FlowTransition<Unit>) {}
        override fun onPreExit() {
          callbackOrder.add("onPreExit")
        }
        override fun onPostExit() {
          callbackOrder.add("onPostExit")
        }
      }

      // par03 schema: par03App (flow) → par03Main (parallel) → [par03Alpha (flow), par03Beta (flow)]
      //               par03App also has par03Page (screen) — navigating there tears down sub-regions
      val alphaFlowRootNode = object : BaseFlowNode<Unit>() {
        override val initial: Target = Target.par03Alpha.par03AlphaScreen
        override val dismissResult: Unit = Unit
      }
      alphaFlowRootNode.addHook(hook)

      val appSchema = Parallel03Schema(
        par03MainSchema = Parallel03MainSchema(
          par03AlphaSchema = Parallel03AlphaSchema(),
          par03BetaSchema = Parallel03BetaSchema(),
        ),
      )
      val alphaNodeBuilder = Par03AlphaNodeBuilder(
        nodeFactory = object : Par03AlphaNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = alphaFlowRootNode
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
          override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
          override fun createPar03AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
          override fun createPar03BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
        },
        schema = Parallel03MainSchema(Parallel03AlphaSchema(), Parallel03BetaSchema()),
      )
      val appNodeBuilder = Par03AppNodeBuilder(
        nodeFactory = object : Par03AppNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par03App.par03Main,
            transitions = listOf(tr("goToPage", Target.par03App.par03Page)),
          )
          override fun createPar03MainNodeBuilder(): NodeBuilder = mainNodeBuilder
          override fun createPar03PageNode(): ScreenNode = TestScreenNode()
        },
        schema = appSchema,
      )
      val sut = NavigationService(
        nodeBuilder = appNodeBuilder,
        onFinishRequest = { _: Unit -> Stay },
      )
      sut.addNodeExtensionPoint(NodeHooksSupportExtensionPoint())

      sut.collectTransitions().test {
        awaitItem() // initial state: par03Alpha sub-region active; hook entry callbacks fired
        callbackOrder.shouldContainExactly("onPreEntry", "onPostEntry")
        callbackOrder.clear()

        // Navigate away from par03Main → sub-regions are torn down → hook exit callbacks fire
        sut.sendEvent(TestEvent("goToPage"))
        awaitItem()
        callbackOrder.shouldContainExactly("onPreExit", "onPostExit")
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("FlowNodeHook fires onPreDispose and onPostDispose on cleanDispose") {
      val callbackOrder = mutableListOf<String>()

      val hook = object : FlowNodeHook<Unit> {
        override fun onPreEntry() {}
        override fun onPostEntry() {}
        override fun onPreTransition(event: Event) {}
        override fun onPostTransition(event: Event, transition: FlowTransition<Unit>) {}
        override fun onPreExit() {}
        override fun onPostExit() {}
        override fun onPreDispose() {
          callbackOrder.add("onPreDispose")
        }
        override fun onPostDispose() {
          callbackOrder.add("onPostDispose")
        }
      }

      val rootFlowNode = object : BaseFlowNode<Unit>() {
        override val initial: Target = Target.app05.intro
        override val dismissResult: Unit = Unit
      }
      rootFlowNode.addHook(hook)

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
      sut.addNodeExtensionPoint(NodeHooksSupportExtensionPoint())
      sut.start()

      sut.cleanDispose()

      callbackOrder.shouldContainExactly("onPreDispose", "onPostDispose")
    }

    should("ScreenNodeHook fires onPreDispose and onPostDispose on cleanDispose") {
      val callbackOrder = mutableListOf<String>()

      val hook = object : ScreenNodeHook {
        override fun onPreEntry() {}
        override fun onPostEntry() {}
        override fun onPreExit() {}
        override fun onPostExit() {}
        override fun onPreDispose() {
          callbackOrder.add("onPreDispose")
        }
        override fun onPostDispose() {
          callbackOrder.add("onPostDispose")
        }
      }

      val screenNode = object : BaseScreenNode() {}
      screenNode.addHook(hook)

      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app05.intro),
            "app.intro" to screenNode,
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      sut.addNodeExtensionPoint(NodeHooksSupportExtensionPoint())
      sut.start()

      sut.cleanDispose()

      callbackOrder.shouldContainExactly("onPreDispose", "onPostDispose")
    }

    should("FlowNodeHook onPreDispose fires before onPostDispose and both after onPreEntry") {
      val callbackOrder = mutableListOf<String>()

      val hook = object : FlowNodeHook<Unit> {
        override fun onPreEntry() {
          callbackOrder.add("onPreEntry")
        }
        override fun onPostEntry() {
          callbackOrder.add("onPostEntry")
        }
        override fun onPreTransition(event: Event) {}
        override fun onPostTransition(event: Event, transition: FlowTransition<Unit>) {}
        override fun onPreExit() {}
        override fun onPostExit() {}
        override fun onPreDispose() {
          callbackOrder.add("onPreDispose")
        }
        override fun onPostDispose() {
          callbackOrder.add("onPostDispose")
        }
      }

      val rootFlowNode = object : BaseFlowNode<Unit>() {
        override val initial: Target = Target.app05.intro
        override val dismissResult: Unit = Unit
      }
      rootFlowNode.addHook(hook)

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
      sut.addNodeExtensionPoint(NodeHooksSupportExtensionPoint())
      sut.start()
      sut.cleanDispose()

      callbackOrder.indexOf("onPreEntry") shouldBeLessThan callbackOrder.indexOf("onPreDispose")
      callbackOrder.indexOf("onPreDispose") shouldBeLessThan callbackOrder.indexOf("onPostDispose")
    }

    should("BaseFlowNode.nodePath is populated by runtime onEntry with the node's absolute path") {
      // The runtime calls onEntry(event, path) with the absolute path to the node. BaseFlowNode
      // captures it into `nodePath` so subclasses can compute absolute sibling RegionIds without
      // hardcoding mount points.
      val capturedPath = mutableListOf<Path>()
      val rootFlowNode = object : BaseFlowNode<Unit>() {
        override val initial: Target = Target.app05.intro
        override val dismissResult: Unit = Unit

        override fun onEntry(event: Event) {
          // Path is set BEFORE onEntry is dispatched, so nodePath is already readable here.
          capturedPath.add(nodePath)
        }
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
      sut.start()
      // Single segment whose name portion is "app" (the exact segment id includes the
      // @file.dot suffix that the schema codegen emits).
      capturedPath.size shouldBe 1
      capturedPath[0].length shouldBe 1
      capturedPath[0].lastSegment().name shouldBe "app"
    }

    should("Node.onEntry legacy single-arg overrides keep working") {
      // Sub-classes that override only the legacy onEntry(event) overload (i.e. existing
      // implementations from before A.3) must continue to receive the call — the default impl
      // on Node delegates from the (event, path) overload to the legacy one.
      val legacyEntryCalls = mutableListOf<String>()
      val rootFlowNode = object : BaseFlowNode<Unit>() {
        override val initial: Target = Target.app05.intro
        override val dismissResult: Unit = Unit

        override fun onEntry(event: Event) {
          legacyEntryCalls.add("onEntry")
        }
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
      sut.start()
      legacyEntryCalls.shouldContainExactly("onEntry")
    }

    should("FlowNodeHook onEntry/onExit fire around Node.onEntry/onExit in documented order") {
      // Per foundation.hookOrder (NavigationService.kt:475-489), the runtime calls each
      // NodeExtensionPoint's onPreEntry, then Node.onEntry(event, path), then onPostEntry.
      // For exit: onPreExit, Node.onExit(event, path), onPostExit.
      // NodeHooksSupportExtensionPoint dispatches each of those to per-node FlowNodeHook
      // callbacks, so the externally-observable order is:
      //   [onPreEntry hook] -> [Node.onEntry] -> [onPostEntry hook]
      //   [onPreExit  hook] -> [Node.onExit ] -> [onPostExit  hook]
      val callbackOrder = mutableListOf<String>()

      val hook = object : FlowNodeHook<String> {
        override fun onPreEntry() {
          callbackOrder.add("hook.onPreEntry")
        }
        override fun onPostEntry() {
          callbackOrder.add("hook.onPostEntry")
        }
        override fun onPreTransition(event: Event) {}
        override fun onPostTransition(event: Event, transition: FlowTransition<String>) {}
        override fun onPreExit() {
          callbackOrder.add("hook.onPreExit")
        }
        override fun onPostExit() {
          callbackOrder.add("hook.onPostExit")
        }
      }

      // The flow we observe: app.login lives under app; we exit it via a Login child-finish.
      val loginFlowNode = object : BaseFlowNode<String>() {
        override val initial: Target = Target.login07.credentials
        override val dismissResult: String = ""

        override fun onEntry(event: Event) {
          callbackOrder.add("node.onEntry")
        }

        override fun onExit(event: Event) {
          callbackOrder.add("node.onExit")
        }
      }
      loginFlowNode.addHook(hook)

      val sut = NavigationService(
        TestNodeBuilder(
          NavService07Schema(),
          mapOf(
            "app" to object : FlowNode<Double> {
              override val initial = Target.app07.login
              override val dismissResult = 0.0
              override fun transition(event: Event): FlowTransition<Double> = when (event) {
                is Nav07AppChildFinishRequest.Login -> NavigateTo(Target.app07.onboarding)
                is Nav07AppChildFinishRequest.Onboarding -> Ignore
                else -> Ignore
              }
            },
            "app.login" to loginFlowNode,
            "app.login.credentials" to TestScreenNode(),
            "app.onboarding" to TestFlowNode(initialTarget = Target.onboarding07.intro),
            "app.onboarding.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Double -> Stay },
      )
      sut.addNodeExtensionPoint(NodeHooksSupportExtensionPoint())

      sut.collectTransitions().test {
        awaitItem() // initial: app.login.credentials — login flow entered

        // Entry order: pre-hook -> Node.onEntry -> post-hook
        callbackOrder.shouldContainExactly("hook.onPreEntry", "node.onEntry", "hook.onPostEntry")
        callbackOrder.clear()

        // Back from credentials -> Finish(login) -> app navigates to onboarding -> login exits.
        sut.sendEvent(Event.Back)
        awaitItem()
        awaitItem()

        // Exit order: pre-hook -> Node.onExit -> post-hook
        callbackOrder.shouldContainExactly("hook.onPreExit", "node.onExit", "hook.onPostExit")
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("ScreenNodeHook onEntry/onExit fire around Node.onEntry/onExit in documented order") {
      // Same contract as FlowNodeHook but for ScreenNode (NodeHooksSupportExtensionPoint dispatches
      // the same NodeExtensionPoint pre/post callbacks to ScreenNodeHook).
      val callbackOrder = mutableListOf<String>()

      val hook = object : ScreenNodeHook {
        override fun onPreEntry() {
          callbackOrder.add("hook.onPreEntry")
        }
        override fun onPostEntry() {
          callbackOrder.add("hook.onPostEntry")
        }
        override fun onPreExit() {
          callbackOrder.add("hook.onPreExit")
        }
        override fun onPostExit() {
          callbackOrder.add("hook.onPostExit")
        }
      }

      // Screen we observe: app.intro. Navigating to app.main exits it.
      val introScreen = object : BaseScreenNode() {
        override fun onEntry(event: Event) {
          callbackOrder.add("node.onEntry")
        }

        override fun onExit(event: Event) {
          callbackOrder.add("node.onExit")
        }
      }
      introScreen.addHook(hook)

      val rootFlowNode = object : BaseFlowNode<Unit>() {
        override val initial: Target = Target.app05.intro
        override val dismissResult: Unit = Unit

        override fun transition(event: Event): FlowTransition<Unit> = when {
          event is TestEvent && event.name == "A" -> NavigateTo(Target.app05.main)
          else -> super.transition(event)
        }
      }

      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to rootFlowNode,
            "app.intro" to introScreen,
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      sut.addNodeExtensionPoint(NodeHooksSupportExtensionPoint())

      sut.collectTransitions().test {
        awaitItem() // initial state: app.intro

        callbackOrder.shouldContainExactly("hook.onPreEntry", "node.onEntry", "hook.onPostEntry")
        callbackOrder.clear()

        sut.sendEvent(TestEvent("A"))
        awaitItem() // navigates to app.main — intro screen exits

        callbackOrder.shouldContainExactly("hook.onPreExit", "node.onExit", "hook.onPostExit")
        cancelAndIgnoreRemainingEvents()
      }
    }

    should(
      "registering/unregistering a hook from inside another hook onEntry callback during dispatch " +
        "does not throw ConcurrentModificationException",
    ) {
      // Per foundation.hookOrder, callOnEntry takes `extensionPoints.toList()` as a snapshot
      // before iterating (NavigationService.kt:476). That snapshot must protect the iteration
      // even when the hook code path triggers add/remove of node extension points while a
      // dispatch is in flight. Without the snapshot this throws ConcurrentModificationException.
      val callbackOrder = mutableListOf<String>()

      // A second extension point we want to add/remove during dispatch.
      val sideExtensionPoint = TestNodeExtensionPoint(
        preEntry = { _, _ -> callbackOrder.add("side.onPreEntry") },
      )

      // Forward-declare the service ref so the hook can call into it.
      lateinit var service: NavigationService<Unit>

      val mutatingHook = object : FlowNodeHook<Unit> {
        override fun onPreEntry() {
          // Mutate the extension-point list from inside a running dispatch. Must NOT throw CME.
          service.addNodeExtensionPoint(sideExtensionPoint)
          service.removeNodeExtensionPoint(sideExtensionPoint)
          callbackOrder.add("hook.onPreEntry")
        }
        override fun onPostEntry() {}
        override fun onPreTransition(event: Event) {}
        override fun onPostTransition(event: Event, transition: FlowTransition<Unit>) {}
        override fun onPreExit() {}
        override fun onPostExit() {}
      }

      val rootFlowNode = object : BaseFlowNode<Unit>() {
        override val initial: Target = Target.app05.intro
        override val dismissResult: Unit = Unit
      }
      rootFlowNode.addHook(mutatingHook)

      service = NavigationService(
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
      service.addNodeExtensionPoint(NodeHooksSupportExtensionPoint())

      // start() drives the InitEvent which calls callOnEntry -> snapshot.forEach -> hook.onPreEntry,
      // and the hook mutates the underlying extension-point list while the snapshot is iterating.
      // The toList() snapshot at NavigationService.kt:476 keeps this safe — no CME.
      service.start()

      // The hook ran without throwing.
      callbackOrder.shouldContainExactly("hook.onPreEntry")
    }

    should("throwing FlowNodeHook propagates and aborts the transition (no runCatching around entry/exit hooks)") {
      // Per foundation.hookOrder: callOnEntry/callOnExit at NavigationService.kt:475-489 invoke
      // snapshot.forEach { it.onPreEntry(node, path) } directly — no runCatching wrapper
      // (only callOnDispose wraps each step). So a throwing FlowNodeHook propagates out of the
      // current dispatch and triggers the outer snapshot-rollback in sendEvent's transition().
      class HookFailure(message: String) : RuntimeException(message)

      // Hook throws on exit. The initial start() must succeed (no exit yet), so we only flip the
      // throw flag after entry settled.
      var armed = false
      val throwingHook = object : FlowNodeHook<String> {
        override fun onPreEntry() {}
        override fun onPostEntry() {}
        override fun onPreTransition(event: Event) {}
        override fun onPostTransition(event: Event, transition: FlowTransition<String>) {}
        override fun onPreExit() {
          if (armed) throw HookFailure("boom from onPreExit")
        }
        override fun onPostExit() {}
      }

      val loginFlowNode = object : BaseFlowNode<String>() {
        override val initial: Target = Target.login07.credentials
        override val dismissResult: String = ""
      }
      loginFlowNode.addHook(throwingHook)

      val sut = NavigationService(
        TestNodeBuilder(
          NavService07Schema(),
          mapOf(
            "app" to object : FlowNode<Double> {
              override val initial = Target.app07.login
              override val dismissResult = 0.0
              override fun transition(event: Event): FlowTransition<Double> = when (event) {
                is Nav07AppChildFinishRequest.Login -> NavigateTo(Target.app07.onboarding)
                is Nav07AppChildFinishRequest.Onboarding -> Ignore
                else -> Ignore
              }
            },
            "app.login" to loginFlowNode,
            "app.login.credentials" to TestScreenNode(),
            "app.onboarding" to TestFlowNode(initialTarget = Target.onboarding07.intro),
            "app.onboarding.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Double -> Stay },
      )
      sut.addNodeExtensionPoint(NodeHooksSupportExtensionPoint())
      sut.start() // succeeds: hook only throws on exit while armed

      armed = true
      // Back from credentials -> Finish(login). The follow-up navigation to onboarding triggers
      // login's onExit, where the hook throws. The throw is NOT swallowed — it propagates out of
      // sendEvent.
      shouldThrow<HookFailure> {
        sut.sendEvent(Event.Back)
      }
    }

    // A real-world app registers two NodeExtensionPoints
    // (NodeHooksSupportExtensionPoint + LeakWatchExtensionPoint) plus a ServiceExtensionPoint
    // (LogTransitionsExtensionPoint). Each is exercised in isolation, but the combined case —
    // each extension receiving every lifecycle callback in registration order without
    // interference — is not. NavigationService dispatches via `extensionPoints.toList().forEach`
    // (NavigationService.kt:475-489 for entry/exit, similar for transition/dispose), so the
    // order is registration order. This locks that contract.
    should(
      "two NodeExtensionPoints registered together both receive lifecycle callbacks in " +
        "registration order without interference",
    ) {
      val log = mutableListOf<String>()

      fun makeExtension(name: String): TestNodeExtensionPoint = TestNodeExtensionPoint(
        preEntry = { _, path -> log.add("$name.onPreEntry:$path") },
        postEntry = { _, path -> log.add("$name.onPostEntry:$path") },
        preExit = { _, path -> log.add("$name.onPreExit:$path") },
        postExit = { _, path -> log.add("$name.onPostExit:$path") },
        preDispose = { _, path -> log.add("$name.onPreDispose:$path") },
        postDispose = { _, path -> log.add("$name.onPostDispose:$path") },
        preTransition = { _, path, _ -> log.add("$name.onPreTransition:$path") },
        postTransition = { _, path, _, _ -> log.add("$name.onPostTransition:$path") },
      )

      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to object : BaseFlowNode<Unit>() {
              override val initial: Target = Target.app05.intro
              override val dismissResult: Unit = Unit
              override fun transition(event: Event): FlowTransition<Unit> = when {
                event is TestEvent && event.name == "go" -> NavigateTo(Target.app05.main)
                else -> super.transition(event)
              }
            },
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      // Register A first, then B — must observe A→B per callback per node.
      sut.addNodeExtensionPoint(makeExtension("A"))
      sut.addNodeExtensionPoint(makeExtension("B"))

      sut.collectTransitions().test {
        awaitItem() // Init → both extensions see onPreEntry/onPostEntry for app and app.intro

        // Every onPreEntry:<path> entry must be IMMEDIATELY followed by the same path's
        // matching B entry, proving registration-order dispatch.
        val entryPairs = log.indices
          .filter { log[it].startsWith("A.onPreEntry:") }
          .map { it to log.getOrNull(it + 1) }
        entryPairs.forEach { (idx, next) ->
          val path = log[idx].substringAfter("A.onPreEntry:")
          next shouldBe "B.onPreEntry:$path"
        }
        val postEntryPairs = log.indices
          .filter { log[it].startsWith("A.onPostEntry:") }
          .map { it to log.getOrNull(it + 1) }
        postEntryPairs.forEach { (idx, next) ->
          val path = log[idx].substringAfter("A.onPostEntry:")
          next shouldBe "B.onPostEntry:$path"
        }
        // Both extensions must have observed entry for every alive node — the count of A's
        // entries equals the count of B's entries (no interference).
        log.count { it.startsWith("A.onPreEntry:") } shouldBe log.count { it.startsWith("B.onPreEntry:") }
        log.count { it.startsWith("A.onPostEntry:") } shouldBe log.count { it.startsWith("B.onPostEntry:") }
        // Sanity: at least one entry actually fired (start mounted two nodes).
        (log.count { it.startsWith("A.onPreEntry:") } >= 2) shouldBe true

        val sizeBeforeNav = log.size
        sut.sendEvent(TestEvent("go"))
        awaitItem() // Navigate to app.main → intro exits, main enters; both extensions see transition + exit + entry

        val deltaLog = log.drop(sizeBeforeNav)

        // onPreTransition pairs: every A.onPreTransition:<path> is followed by B.onPreTransition:<path>
        val txPairs = deltaLog.indices
          .filter { deltaLog[it].startsWith("A.onPreTransition:") }
          .map { it to deltaLog.getOrNull(it + 1) }
        (txPairs.isNotEmpty()) shouldBe true
        txPairs.forEach { (idx, next) ->
          val path = deltaLog[idx].substringAfter("A.onPreTransition:")
          next shouldBe "B.onPreTransition:$path"
        }
        // onPostTransition pairs
        val postTxPairs = deltaLog.indices
          .filter { deltaLog[it].startsWith("A.onPostTransition:") }
          .map { it to deltaLog.getOrNull(it + 1) }
        (postTxPairs.isNotEmpty()) shouldBe true
        postTxPairs.forEach { (idx, next) ->
          val path = deltaLog[idx].substringAfter("A.onPostTransition:")
          next shouldBe "B.onPostTransition:$path"
        }
        // onPreExit pairs (intro screen exits)
        val exitPairs = deltaLog.indices
          .filter { deltaLog[it].startsWith("A.onPreExit:") }
          .map { it to deltaLog.getOrNull(it + 1) }
        (exitPairs.isNotEmpty()) shouldBe true
        exitPairs.forEach { (idx, next) ->
          val path = deltaLog[idx].substringAfter("A.onPreExit:")
          next shouldBe "B.onPreExit:$path"
        }
        val postExitPairs = deltaLog.indices
          .filter { deltaLog[it].startsWith("A.onPostExit:") }
          .map { it to deltaLog.getOrNull(it + 1) }
        (postExitPairs.isNotEmpty()) shouldBe true
        postExitPairs.forEach { (idx, next) ->
          val path = deltaLog[idx].substringAfter("A.onPostExit:")
          next shouldBe "B.onPostExit:$path"
        }
        // Per-callback counts must match between A and B in the delta (no interference).
        deltaLog.count { it.startsWith("A.onPreEntry:") } shouldBe deltaLog.count { it.startsWith("B.onPreEntry:") }
        deltaLog.count { it.startsWith("A.onPostEntry:") } shouldBe deltaLog.count { it.startsWith("B.onPostEntry:") }
        deltaLog.count { it.startsWith("A.onPreExit:") } shouldBe deltaLog.count { it.startsWith("B.onPreExit:") }
        deltaLog.count { it.startsWith("A.onPostExit:") } shouldBe deltaLog.count { it.startsWith("B.onPostExit:") }
        deltaLog.count { it.startsWith("A.onPreTransition:") } shouldBe
          deltaLog.count { it.startsWith("B.onPreTransition:") }
        deltaLog.count { it.startsWith("A.onPostTransition:") } shouldBe
          deltaLog.count { it.startsWith("B.onPostTransition:") }

        cancelAndIgnoreRemainingEvents()
      }

      // cleanDispose drives onPreDispose/onPostDispose for every alive node — verify both
      // extensions still see those in registration order.
      val sizeBeforeDispose = log.size
      sut.cleanDispose()
      val disposeLog = log.drop(sizeBeforeDispose)

      val disposePairs = disposeLog.indices
        .filter { disposeLog[it].startsWith("A.onPreDispose:") }
        .map { it to disposeLog.getOrNull(it + 1) }
      (disposePairs.isNotEmpty()) shouldBe true
      disposePairs.forEach { (idx, next) ->
        val path = disposeLog[idx].substringAfter("A.onPreDispose:")
        next shouldBe "B.onPreDispose:$path"
      }
      val postDisposePairs = disposeLog.indices
        .filter { disposeLog[it].startsWith("A.onPostDispose:") }
        .map { it to disposeLog.getOrNull(it + 1) }
      (postDisposePairs.isNotEmpty()) shouldBe true
      postDisposePairs.forEach { (idx, next) ->
        val path = disposeLog[idx].substringAfter("A.onPostDispose:")
        next shouldBe "B.onPostDispose:$path"
      }
      disposeLog.count { it.startsWith("A.onPreDispose:") } shouldBe
        disposeLog.count { it.startsWith("B.onPreDispose:") }
      disposeLog.count { it.startsWith("A.onPostDispose:") } shouldBe
        disposeLog.count { it.startsWith("B.onPostDispose:") }
    }
  })
