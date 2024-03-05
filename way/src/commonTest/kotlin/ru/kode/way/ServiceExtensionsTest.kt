package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import ru.kode.way.nav04.NavService04Schema
import ru.kode.way.nav04.AppChildFinishRequest as Nav04AppChildFinishRequest
import ru.kode.way.nav04.app as app04
import ru.kode.way.nav04.permissions as permissions04
import ru.kode.way.nav04.profile as profile04

class ServiceExtensionsTest : ShouldSpec({
  should("call entry/exit extension methods") {
    val preTransition: MutableList<Pair<Event, NavigationState>> = mutableListOf()
    val postTransition: MutableList<Pair<Event, NavigationState>> = mutableListOf()

    val point = TestServiceExtensionPoint<Unit>(
      preTransition = { _, event, state -> preTransition.add(event to state) },
      postTransition = { _, event, state -> postTransition.add(event to state) },
    )
    val sut = NavigationService(
      TestNodeBuilder(
        NavService04Schema(),
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app04.permissions,
            transitions = listOf(
              tr(on = "C", target = Target.app04.profile),
              tr<Nav04AppChildFinishRequest.Permissions>(Finish(Unit))
            )
          ),
          "app.permissions" to TestFlowNode(
            initialTarget = Target.permissions04.intro,
          ),
          "app.permissions.intro" to TestScreenNode(),
          "app.permissions.request" to TestScreenNode(),
          "app.profile" to TestFlowNode(
            initialTarget = Target.profile04.main,
            transitions = listOf(
              tr(on = "C", target = Target.app04.permissions),
              tr<Nav04AppChildFinishRequest.Permissions>(Finish(Unit))
            )
          ),
          "app.profile.main" to TestScreenNode()
        )
      ),
      onFinishRequest = { _: Unit -> Stay },
    )
    sut.addServiceExtensionPoint(point)

    sut.collectTransitions().test {
      awaitItem()
      preTransition.single().first shouldBe InitEvent(payload = null)
      postTransition.single().first shouldBe InitEvent(payload = null)
      postTransition.single().second.active shouldBe "app.permissions.intro"

      sut.sendEvent(TestEvent("C"))
      awaitItem()

      preTransition[1].first shouldBe TestEvent("C")
      preTransition[1].second.active shouldBe "app.permissions.intro"
      preTransition[1].second.alive.shouldContainExactly("app", "app.permissions", "app.permissions.intro")
      postTransition[1].first shouldBe TestEvent("C")
      postTransition[1].second.alive.shouldContainExactly("app", "app.profile", "app.profile.main")
    }
  }
})
