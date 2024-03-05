package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import ru.kode.way.nav04.NavService04Schema
import ru.kode.way.nav04.AppChildFinishRequest as Nav04AppChildFinishRequest
import ru.kode.way.nav04.app as app04
import ru.kode.way.nav04.permissions as permissions04
import ru.kode.way.nav04.profile as profile04

class NodeExtensionsTest : ShouldSpec({
  should("call entry/exit extension methods") {
    val preEntry: MutableList<Pair<Node, String>> = mutableListOf()
    val postEntry: MutableList<Pair<Node, String>> = mutableListOf()
    val preExit: MutableList<Pair<Node, String>> = mutableListOf()
    val postExit: MutableList<Pair<Node, String>> = mutableListOf()

    val point = TestNodeExtensionPoint(
      preEntry = { node, path -> preEntry += node to path.toString() },
      postEntry = { node, path -> postEntry += node to path.toString() },
      preExit = { node, path -> preExit += node to path.toString() },
      postExit = { node, path -> postExit += node to path.toString() },
    )
    val sut = NavigationService(
      TestNodeBuilder(
        NavService04Schema(),
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app04.permissions,
            transitions = listOf(
              tr(on = "C", target = Target.app04.profile),
              tr<Nav04AppChildFinishRequest.Permissions>(Finish(Unit)),
              tr<Nav04AppChildFinishRequest.Profile>(Finish(Unit))
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
            )
          ),
          "app.profile.main" to TestScreenNode()
        )
      ),
      onFinishRequest = { _: Unit -> Stay },
    )
    sut.addNodeExtensionPoint(point)

    sut.collectTransitions().test {
      awaitItem()

      preEntry.map { it.second }.shouldContainExactly("app", "app.permissions", "app.permissions.intro")

      sut.sendEvent(TestEvent("C"))
      awaitItem()

      preEntry.map { it.second }.shouldContainExactly(
        "app", "app.permissions", "app.permissions.intro", "app.profile", "app.profile.main"
      )
      preExit.map { it.second }.shouldContainExactly(
        "app.permissions.intro", "app.permissions"
      )

      sut.sendEvent(TestEvent("C"))
      awaitItem()

      preEntry.map { it.second }.shouldContainExactly(
        "app", "app.permissions", "app.permissions.intro", "app.profile", "app.profile.main",
        "app.permissions", "app.permissions.intro",
      )
      preExit.map { it.second }.shouldContainExactly(
        "app.permissions.intro", "app.permissions", "app.profile.main", "app.profile"
      )
    }
  }

  should("call preTransition/postTransition extension methods") {
    val preTransition: MutableList<Pair<String, Event>> = mutableListOf()
    val postTransition: MutableList<Pair<String, Event>> = mutableListOf()

    val point = TestNodeExtensionPoint(
      preTransition = { _, path, event -> preTransition += path.toString() to event },
      postTransition = { _, path, event, _ -> postTransition += path.toString() to event },
    )
    val sut = NavigationService(
      TestNodeBuilder(
        NavService04Schema(),
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app04.permissions,
            transitions = listOf(
              tr(on = "C", target = Target.app04.profile),
              tr<Nav04AppChildFinishRequest.Permissions>(Finish(Unit)),
              tr<Nav04AppChildFinishRequest.Profile>(Finish(Unit))
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
              tr(on = "C", target = Target.app04.permissions)
            )
          ),
          "app.profile.main" to TestScreenNode()
        )
      ),
      onFinishRequest = { _: Unit -> Stay },
    )
    sut.addNodeExtensionPoint(point)

    sut.collectTransitions().test {
      awaitItem()

      sut.sendEvent(TestEvent("C"))
      awaitItem()

      preTransition.map { it.first to (it.second as TestEvent).name }
        .shouldContainExactly("app.permissions.intro" to "C", "app.permissions" to "C", "app" to "C")
      postTransition.map { it.first to (it.second as TestEvent).name }
        .shouldContainExactly("app.permissions.intro" to "C", "app.permissions" to "C", "app" to "C")

      sut.sendEvent(TestEvent("C"))
      awaitItem()

      preTransition.map { it.first to (it.second as TestEvent).name }
        .shouldContainExactly(
          "app.permissions.intro" to "C", "app.permissions" to "C", "app" to "C",
          "app.profile.main" to "C", "app.profile" to "C",
        )
      postTransition.map { it.first to (it.second as TestEvent).name }
        .shouldContainExactly(
          "app.permissions.intro" to "C", "app.permissions" to "C", "app" to "C",
          "app.profile.main" to "C", "app.profile" to "C"
        )
    }
  }
})
