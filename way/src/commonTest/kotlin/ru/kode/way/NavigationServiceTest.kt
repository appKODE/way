package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ru.kode.way.nav01.NavService01Schema
import ru.kode.way.nav02.NavService02Schema
import ru.kode.way.nav04.NavService04Schema
import ru.kode.way.nav05.NavService05Schema
import ru.kode.way.nav06.NavService06Schema
import ru.kode.way.nav07.NavService07Schema
import ru.kode.way.nav08.NavService08Schema
import ru.kode.way.nav01.app as app01
import ru.kode.way.nav02.app as app02
import ru.kode.way.nav02.permissions as permissions02
import ru.kode.way.nav04.app as app04
import ru.kode.way.nav04.permissions as permissions04
import ru.kode.way.nav04.profile as profile04
import ru.kode.way.nav05.app as app05
import ru.kode.way.nav06.app as app06
import ru.kode.way.nav07.app as app07
import ru.kode.way.nav07.login as login07
import ru.kode.way.nav07.onboarding as onboarding07
import ru.kode.way.nav08.app as app08
import ru.kode.way.nav08.login as login08
import ru.kode.way.nav08.onboarding as onboarding08

class NavigationServiceTest : ShouldSpec({
  should("switch to direct initial state") {
    val sut = NavigationService(
      NavService01Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(initialTarget = Target.app01.intro),
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
      NavService02Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app02.permissions { Finish(Unit) }
          ),
          "app.permissions" to TestFlowNode(
            initialTarget = Target.permissions02.intro
          ),
          "app.permissions.intro" to TestScreenNode()
        )
      ),
    )

    sut.collectTransitions().test {
      awaitItem().active shouldBe "app.permissions.intro"
    }
  }

  // TODO re-enable and adapt when compound-schema import will be done
  //   currently this is impossible because we can't have onboarding as a child **flow**-node of login,
  //   because they are described in a single dot file (enforced by NOTE_GROUPING_NODES_BY_FLOW_RULE)
  //   Once schema composition will be available login + onboarding can be extracted into a different file
  //   and then this test can be performed
//  should("switch to initial state creating all nested child screen nodes") {
//    val sut = NavigationService(
//      NavService03Schema(),
//      TestNodeBuilder(
//        mapOf(
//          "app" to TestFlowNode(
//            initialTarget = Target.app03.login { Finish(Unit) }
//          ),
//          "app.login" to TestFlowNode(
//            initialTarget = Target.login03.onboarding { Finish(Unit) }
//          ),
//          "app.login.onboarding" to TestFlowNode(
//            initialTarget = Target.onboarding03.intro
//          ),
//          "app.login.onboarding.intro" to TestScreenNode()
//        )
//      ),
//    )
//
//    sut.collectTransitions().test {
//      awaitItem().active shouldBe "app.login.onboarding.intro"
//    }
//  }

  should("ignore event completely if no node defines an actionable transition") {
    val sut = NavigationService(
      NavService02Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app02.permissions { Finish(Unit) }
          ),
          "app.permissions" to TestFlowNode(
            initialTarget = Target.permissions02.intro,
            transitions = listOf(
              tr(on = "A", target = Target.permissions02.intro)
            )
          ),
          "app.permissions.intro" to TestScreenNode()
        )
      ),
    )

    sut.collectTransitions().test {
      awaitItem()

      sut.sendEvent(TestEvent("B"))

      // nothing should happen
      awaitItem().active shouldBe "app.permissions.intro"
    }
  }

  should("process events in a bottom-up order") {
    val sut = NavigationService(
      NavService04Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app04.permissions { Finish(Unit) },
            transitions = listOf(
              tr(on = "C", target = Target.app04.profile { Ignore })
            )
          ),
          "app.permissions" to TestFlowNode(
            initialTarget = Target.permissions04.intro,
            transitions = listOf(
              tr(on = "B", target = Target.permissions04.intro)
            )
          ),
          "app.permissions.intro" to TestScreenNode(
            transitions = listOf(
              trs(on = "A", target = Target.permissions04.request)
            )
          ),
          "app.permissions.request" to TestScreenNode(),
          "app.profile" to TestFlowNode(
            initialTarget = Target.profile04.main,
          ),
          "app.profile.main" to TestScreenNode()
        )
      ),
    )

    sut.collectTransitions().test {
      awaitItem()

      sut.sendEvent(TestEvent("A"))
      awaitItem().active shouldBe "app.permissions.request"

      sut.sendEvent(TestEvent("B"))
      awaitItem().active shouldBe "app.permissions.intro"

      sut.sendEvent(TestEvent("C"))
      awaitItem().active shouldBe "app.profile.main"
    }
  }

  should("replace nodes when transitioning between sibling nodes") {
    val sut = NavigationService(
      NavService05Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app05.intro,
            transitions = listOf(
              tr(on = "A", target = Target.app05.main),
              tr(on = "B", target = Target.app05.test),
              tr(on = "C", target = Target.app05.main),
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

  should("append to live nodes when transitioning to child screen node sequentially") {
    val sut = NavigationService(
      NavService06Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app06.intro,
            transitions = listOf(
              tr(on = "A", target = Target.app06.main),
              tr(on = "B", target = Target.app06.test),
            )
          ),
          "app.intro" to TestScreenNode(),
          "app.intro.main" to TestScreenNode(),
          "app.intro.main.test" to TestScreenNode(),
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
        alive.shouldContainInOrder("app", "app.intro", "app.intro.main")
        active shouldBe "app.intro.main"
      }

      sut.sendEvent(TestEvent("B"))
      awaitItem().apply {
        alive.shouldContainInOrder("app", "app.intro", "app.intro.main", "app.intro.main.test")
        active shouldBe "app.intro.main.test"
      }
    }
  }

  should("append to live nodes when transitioning to grand child screen node") {
    val sut = NavigationService(
      NavService06Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app06.intro,
            transitions = listOf(
              tr(on = "A", target = Target.app06.test),
            )
          ),
          "app.intro" to TestScreenNode(),
          "app.intro.main" to TestScreenNode(),
          "app.intro.main.test" to TestScreenNode(),
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
        alive.shouldContainInOrder("app", "app.intro", "app.intro.main", "app.intro.main.test")
        active shouldBe "app.intro.main.test"
      }
    }
  }

  should("call onFinish to inform parent flow of result") {
    val sut = NavigationService(
      NavService07Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app07.onboarding { r: Int ->
              if (r == 42) {
                NavigateTo(Target.app07.login { Finish(Unit) })
              } else {
                Finish(Unit)
              }
            }
          ),
          "app.onboarding" to TestFlowNodeWithResult(
            initialTarget = Target.onboarding07.intro,
            dismissResult = 33,
            transitions = listOf(
              tr(on = "A", Finish(42))
            )
          ),
          "app.onboarding.intro" to TestScreenNode(),
          "app.login" to TestFlowNode(
            initialTarget = Target.login07.credentials,
          ),
          "app.login.credentials" to TestScreenNode()
        )
      ),
    )

    sut.collectTransitions().test {
      awaitItem()

      sut.sendEvent(TestEvent("A"))

      awaitItem().active shouldBe "app.login.credentials"
    }
  }

  should("call onFinish to inform parent flow of result from non-initial node in the sub-flow") {
    val sut = NavigationService(
      NavService08Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app08.onboarding { r: Int ->
              if (r == 42) {
                NavigateTo(Target.app08.login { Finish(Unit) })
              } else {
                Finish(Unit)
              }
            }
          ),
          "app.onboarding" to TestFlowNodeWithResult(
            initialTarget = Target.onboarding08.intro,
            dismissResult = 33,
            transitions = listOf(
              tr(on = "A", target = Target.onboarding08.page1),
              tr(on = "B", Finish(42))
            )
          ),
          "app.onboarding.intro" to TestScreenNode(),
          "app.onboarding.page1" to TestScreenNode(),
          "app.login" to TestFlowNode(
            initialTarget = Target.login08.credentials,
          ),
          "app.login.credentials" to TestScreenNode()
        )
      ),
    )

    sut.collectTransitions().test {
      awaitItem()

      sut.sendEvent(TestEvent("A"))
      awaitItem().active shouldBe "app.onboarding.page1"

      sut.sendEvent(TestEvent("B"))
      awaitItem().active shouldBe "app.login.credentials"
    }
  }
})

private val NavigationState.active get() = this.regions.values.first().active.toString()
private val NavigationState.alive get() = this.regions.values.first().alive.map { it.toString() }
private val NavigationState.nodes get() = this.regions.values.first().nodes

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
