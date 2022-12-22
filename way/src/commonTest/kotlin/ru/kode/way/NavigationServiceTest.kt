package ru.kode.way

import app.cash.turbine.test
import io.kotest.assertions.fail
import io.kotest.core.spec.style.ShouldSpec
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
      awaitItem().active shouldBe Path("app", "intro")
    }
  }

  should("f: switch to initial state requiring sub-flow transition") {
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
      awaitItem().active shouldBe Path("app", "permissions", "intro")
    }
  }

  should("switch to initial state creating all nested child screen nodes") {
    fail("TODO")
  }

  should("process events in a bottom-up order") {
    fail("TODO")
  }
})

private val NavigationState.active get() = this.regions.values.first().active

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
