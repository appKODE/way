package ru.kode.way

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import ru.kode.way.nav01.NavService01Schema
import ru.kode.way.nav01.app as app01

// Reads NavigationService internals via JVM reflection, so it can't live in commonTest
class DisposeLifecycleReflectionTest :
  ShouldSpec({
    should("addTransitionListener after dispose() does not register the listener (no leak)") {
      // The leak fix asserts that addTransitionListener returns early when isDisposed,
      // so the listener instance is not retained in the internal `listeners` ArrayList.
      // Without the fix the listener would stay in the list forever and never be invoked
      // (send is a no-op after dispose), leaking the closure and everything it captures.
      //
      // Assert via reflection on the private `listeners` field so the test fails when the
      // listener IS appended post-dispose, even though send post-dispose hides the bug
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
  })
