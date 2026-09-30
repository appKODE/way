package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import ru.kode.way.nav16.AppChildFinishRequest
import ru.kode.way.nav16.AppNodeBuilder
import ru.kode.way.nav16.NavService16PermSchema
import ru.kode.way.nav16.NavService16Schema
import ru.kode.way.nav16.PermNodeBuilder
import ru.kode.way.nav16.app
import ru.kode.way.nav16.perm

class SchemaFanInTest :
  ShouldSpec({
    should("enter an imported schema through each of its parents with that path's payloads and finish to it") {
      val permReasons = mutableMapOf<Node, String>()
      val finishedIn = mutableListOf<String>()
      val permFactory = object : PermNodeBuilder.Factory {
        override fun createRootNode(reason: String): FlowNode<*> = TestFlowNodeWithResult(
          initialTarget = Target.perm.permIntro,
          dismissResult = "",
          transitions = listOf(tr("Done", Finish("done-$reason"))),
        ).also { permReasons[it] = reason }
        override fun createPermIntroNode() = TestScreenNode()
      }
      fun parentScreen(id: String, payload: Any, back: Target) = TestScreenNode(
        payload = payload,
        transitions = listOf(
          TestScreenTransitionSpec(
            eventMatcher = { event ->
              (event is AppChildFinishRequest.Perm).also {
                if (it) {
                  finishedIn +=
                    "$id:${(event as AppChildFinishRequest.Perm).result}"
                }
              }
            },
            transition = NavigateTo(back),
          ),
        ),
      )
      val sut = NavigationService(
        AppNodeBuilder(
          object : AppNodeBuilder.Factory {
            override fun createRootNode() = TestFlowNode(
              initialTarget = Target.app.a(aId = 1),
              transitions = listOf(
                // `a` is alive: the short target keeps its payload
                tr("ViaA", Target.app.permViaA(reason = "r-a")),
                // `b` is not alive: the full target carries its payload
                tr("ViaB", Target.app.permViaB(bId = "b-1", reason = "r-b")),
              ),
            )
            override fun createANode(aId: Int) = parentScreen("a", aId, Target.app.a(aId))
            override fun createBNode(bId: String) = parentScreen("b", bId, Target.app.b(bId))
            override fun createPermNodeBuilder(reason: String): NodeBuilder =
              PermNodeBuilder(permFactory, NavService16PermSchema())
          },
          NavService16Schema(NavService16PermSchema()),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.a"

        sut.sendEvent(TestEvent("ViaA"))
        awaitItem().apply {
          alive shouldBe listOf("app", "app.a", "app.a.perm", "app.a.perm.permIntro")
          (aliveNodes["app.a"] as TestScreenNode).payload shouldBe 1
          permReasons[aliveNodes.getValue("app.a.perm")] shouldBe "r-a"
        }

        sut.sendEvent(TestEvent("Done"))
        // perm finishes with a result, then its parent receives the finish request event
        awaitItem().active shouldBe "app.a.perm.permIntro"
        awaitItem().active shouldBe "app.a"
        finishedIn shouldBe listOf("a:done-r-a")

        sut.sendEvent(TestEvent("ViaB"))
        awaitItem().apply {
          alive shouldBe listOf("app", "app.b", "app.b.perm", "app.b.perm.permIntro")
          (aliveNodes["app.b"] as TestScreenNode).payload shouldBe "b-1"
          permReasons[aliveNodes.getValue("app.b.perm")] shouldBe "r-b"
        }

        sut.sendEvent(TestEvent("Done"))
        awaitItem().active shouldBe "app.b.perm.permIntro"
        awaitItem().active shouldBe "app.b"
        finishedIn shouldBe listOf("a:done-r-a", "b:done-r-b")
      }
    }
  })
