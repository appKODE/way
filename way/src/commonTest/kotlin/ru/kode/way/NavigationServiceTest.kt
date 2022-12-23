package ru.kode.way

import app.cash.turbine.test
import io.kotest.assertions.fail
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class NavigationServiceTest : ShouldSpec({
  should("switch to direct initial state") {
    val sut = NavigationService(
      object : Schema {
        override val regions: List<Path> = listOf(Path("app"))
      },
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(initialScreen = "intro"),
          "app.intro" to TestScreenNode(),
        )
      ),
    )

    sut.collectTransitions().test {
      awaitItem().active shouldBe "app.intro"
    }
  }

  should("switch to initial state requiring sub-flow transition") {
    val sut = NavigationService(
      object : Schema {
        override val regions: List<Path> = listOf(Path("app"))
      },
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = FlowTarget(Path("permissions"), onFinish = { _: Int -> Finish(Unit) })
          ),
          "app.permissions" to TestFlowNode(
            initialScreen = "intro"
          ),
          "app.permissions.intro" to TestScreenNode()
        )
      ),
    )

    sut.collectTransitions().test {
      awaitItem().active shouldBe "app.permissions.intro"
    }
  }

  should("switch to initial state creating all nested child screen nodes") {
    val sut = NavigationService(
      object : Schema {
        override val regions: List<Path> = listOf(Path("app"))
      },
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = FlowTarget(Path("login"), onFinish = { _: Int -> Finish(Unit) })
          ),
          "app.login" to TestFlowNode(
            initialTarget = FlowTarget(Path("onboarding"), onFinish = { _: Int -> Finish(Unit) })
          ),
          "app.login.onboarding" to TestFlowNode(
            initialScreen = "intro"
          ),
          "app.login.onboarding.intro" to TestScreenNode()
        )
      ),
    )

    sut.collectTransitions().test {
      awaitItem().active shouldBe "app.login.onboarding.intro"
    }
  }

  should("replace nodes when transitioning between sibling nodes") {
    val sut = NavigationService(
      object : Schema {
        override val regions: List<Path> = listOf(Path("app"))
      },
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialScreen = "intro",
            transitions = listOf(
              tr(on = "A", target = "main"),
              tr(on = "B", target = "test"),
              tr(on = "C", target = "main"),
            )
          ),
          "app.intro" to TestScreenNode(),
          "app.main" to TestScreenNode(),
          "app.test" to TestScreenNode(),
        )
      ),
    )


    sut.collectTransitions().test {
      awaitItem().apply {
        alive.shouldContainInOrder("app", "app.intro")
        active shouldBe "app.intro"
      }

      sut.sendEvent(TestEvent("A"))
      awaitItem().apply {
        alive.shouldContainInOrder("app", "app.main")
        active shouldBe "app.main"
      }

      sut.sendEvent(TestEvent("B"))
      awaitItem().apply {
        alive.shouldContainInOrder("app", "app.test")
        active shouldBe "app.test"
      }

      sut.sendEvent(TestEvent("C"))
      awaitItem().apply {
        alive.shouldContainInOrder("app", "app.main")
        active shouldBe "app.main"
      }
    }
  }

  should("process events in a bottom-up order") {
    fail("TODO")
  }
})

private val NavigationState.active get() = this.regions.values.first().active.toString()
private val NavigationState.alive get() = this.regions.values.first().alive.map { it.toString() }

private fun NavigationService.collectTransitions(): Flow<NavigationState> {
  return callbackFlow {
    val listener = { state: NavigationState ->
      trySend(state)
      Unit
    }
    this@collectTransitions.addTransitionListener(listener)
    start()
    awaitClose { this@collectTransitions.removeTransitionListener(listener) }
  }
}
