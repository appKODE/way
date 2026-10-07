package ru.kode.way

import app.cash.turbine.test
import io.kotest.assertions.throwables.shouldThrow
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
    should("keep the node builder of a parameterized schema when a transition which re-targets it fails") {
      val failingReasons = mutableSetOf("r-2")
      val sut = NavigationService(
        AppNodeBuilder(
          object : AppNodeBuilder.Factory {
            override fun createRootNode() = TestFlowNode(
              initialTarget = Target.app.a(aId = 1),
              transitions = listOf(
                tr("First", Target.app.permViaA(reason = "r-1")),
                tr("Second", Target.app.permViaA(reason = "r-2")),
              ),
            )
            override fun createANode(aId: Int) = TestScreenNode(payload = aId)
            override fun createBNode(bId: String) = TestScreenNode(payload = bId)
            override fun createPermNodeBuilder(reason: String): NodeBuilder = PermNodeBuilder(
              object : PermNodeBuilder.Factory {
                override fun createRootNode(reason: String): FlowNode<*> = TestFlowNodeWithResult(
                  initialTarget = Target.perm.permIntro,
                  dismissResult = "",
                  transitions = listOf(tr("Details", Target.perm.permDetails)),
                  onEntryImpl = { if (failingReasons.remove(reason)) error("entry of $reason failed") },
                )
                override fun createPermIntroNode() = TestScreenNode(payload = reason)
                override fun createPermDetailsNode() = TestScreenNode(payload = reason)
              },
              NavService16PermSchema(),
            )
          },
          NavService16Schema(NavService16PermSchema()),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      sut.collectTransitions().test {
        awaitItem()
        sut.send(TestEvent("First"))
        awaitItem().active shouldBe "app.a.perm.permIntro"

        shouldThrow<IllegalStateException> { sut.send(TestEvent("Second")) }

        // the schema is alive with its first argument still, so is its node builder
        sut.send(TestEvent("Details"))
        (awaitItem().aliveNodes["app.a.perm.permDetails"] as TestScreenNode).payload shouldBe "r-1"
      }
    }

    should("drop the node builder of a parameterized schema which a failed transition has created") {
      val failingReasons = mutableSetOf("r-2")
      val sut = NavigationService(
        AppNodeBuilder(
          object : AppNodeBuilder.Factory {
            override fun createRootNode() = TestFlowNode(
              initialTarget = Target.app.a(aId = 1),
              transitions = listOf(
                tr("First", Target.app.permViaA(reason = "r-1")),
                tr("Second", Target.app.permViaA(reason = "r-2")),
              ),
            )
            override fun createANode(aId: Int) = TestScreenNode(payload = aId)
            override fun createBNode(bId: String) = TestScreenNode(payload = bId)
            override fun createPermNodeBuilder(reason: String): NodeBuilder = PermNodeBuilder(
              object : PermNodeBuilder.Factory {
                override fun createRootNode(reason: String): FlowNode<*> = TestFlowNodeWithResult(
                  initialTarget = Target.perm.permIntro,
                  dismissResult = "",
                  transitions = listOf(tr("Details", Target.perm.permDetails)),
                  onEntryImpl = { if (failingReasons.remove(reason)) error("entry of $reason failed") },
                )
                override fun createPermIntroNode() = TestScreenNode(payload = reason)
                override fun createPermDetailsNode() = TestScreenNode(payload = reason)
              },
              NavService16PermSchema(),
            )
          },
          NavService16Schema(NavService16PermSchema()),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      sut.collectTransitions().test {
        awaitItem()
        shouldThrow<IllegalStateException> { sut.send(TestEvent("Second")) }

        sut.send(TestEvent("First"))
        (awaitItem().aliveNodes["app.a.perm.permIntro"] as TestScreenNode).payload shouldBe "r-1"
      }
    }

    should("keep the node builder which a transition has created when a hook throws after that transition") {
      var builders = 0
      val sut = NavigationService(
        AppNodeBuilder(
          object : AppNodeBuilder.Factory {
            override fun createRootNode() = TestFlowNode(
              initialTarget = Target.app.a(aId = 1),
              transitions = listOf(tr("First", Target.app.permViaA(reason = "r-1"))),
            )
            override fun createANode(aId: Int) = TestScreenNode(payload = aId)
            override fun createBNode(bId: String) = TestScreenNode(payload = bId)
            override fun createPermNodeBuilder(reason: String): NodeBuilder = PermNodeBuilder(
              object : PermNodeBuilder.Factory {
                override fun createRootNode(reason: String): FlowNode<*> = TestFlowNodeWithResult(
                  initialTarget = Target.perm.permIntro,
                  dismissResult = "",
                  transitions = listOf(tr("Details", Target.perm.permDetails)),
                )
                override fun createPermIntroNode() = TestScreenNode()
                override fun createPermDetailsNode() = TestScreenNode()
              },
              NavService16PermSchema(),
            ).also { builders++ }
          },
          NavService16Schema(NavService16PermSchema()),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      sut.addServiceExtensionPoint(object : ServiceExtensionPoint<Unit> {
        override fun onPostTransition(service: NavigationService<Unit>, event: Event, state: NavigationState) {
          if (event == TestEvent("First")) error("hook failed")
        }
      })
      sut.start()

      // the transition is committed, the nodes of the schema are alive: they keep the builder which has made them
      shouldThrow<IllegalStateException> { sut.send(TestEvent("First")) }
      sut.send(TestEvent("Details"))
      builders shouldBe 1
    }

    should("build the initial node of a re-targeted parameterized schema with its new argument") {
      val sut = NavigationService(
        AppNodeBuilder(
          object : AppNodeBuilder.Factory {
            override fun createRootNode() = TestFlowNode(
              initialTarget = Target.app.a(aId = 1),
              transitions = listOf(
                tr("First", Target.app.permViaA(reason = "r-1")),
                tr("Second", Target.app.permViaA(reason = "r-2")),
              ),
            )
            override fun createANode(aId: Int) = TestScreenNode(payload = aId)
            override fun createBNode(bId: String) = TestScreenNode(payload = bId)
            override fun createPermNodeBuilder(reason: String): NodeBuilder = PermNodeBuilder(
              object : PermNodeBuilder.Factory {
                override fun createRootNode(reason: String): FlowNode<*> = TestFlowNodeWithResult(
                  initialTarget = Target.perm.permIntro,
                  dismissResult = "",
                  transitions = listOf(tr("Details", Target.perm.permDetails)),
                )
                override fun createPermIntroNode() = TestScreenNode(payload = reason)
                override fun createPermDetailsNode() = TestScreenNode()
              },
              NavService16PermSchema(),
            )
          },
          NavService16Schema(NavService16PermSchema()),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      sut.collectTransitions().test {
        awaitItem()
        sut.send(TestEvent("First"))
        (awaitItem().aliveNodes["app.a.perm.permIntro"] as TestScreenNode).payload shouldBe "r-1"

        // the initial node is not alive when the schema is re-targeted
        sut.send(TestEvent("Details"))
        awaitItem().active shouldBe "app.a.perm.permDetails"

        sut.send(TestEvent("Second"))
        awaitItem().apply {
          active shouldBe "app.a.perm.permIntro"
          (aliveNodes["app.a.perm.permIntro"] as TestScreenNode).payload shouldBe "r-2"
        }
      }
    }

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
        override fun createPermDetailsNode() = TestScreenNode()
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

        sut.send(TestEvent("ViaA"))
        awaitItem().apply {
          alive shouldBe listOf("app", "app.a", "app.a.perm", "app.a.perm.permIntro")
          (aliveNodes["app.a"] as TestScreenNode).payload shouldBe 1
          permReasons[aliveNodes.getValue("app.a.perm")] shouldBe "r-a"
        }

        sut.send(TestEvent("Done"))
        // perm finishes with a result, then its parent receives the finish request event
        awaitItem().active shouldBe "app.a.perm.permIntro"
        awaitItem().active shouldBe "app.a"
        finishedIn shouldBe listOf("a:done-r-a")

        sut.send(TestEvent("ViaB"))
        awaitItem().apply {
          alive shouldBe listOf("app", "app.b", "app.b.perm", "app.b.perm.permIntro")
          (aliveNodes["app.b"] as TestScreenNode).payload shouldBe "b-1"
          permReasons[aliveNodes.getValue("app.b.perm")] shouldBe "r-b"
        }

        sut.send(TestEvent("Done"))
        awaitItem().active shouldBe "app.b.perm.permIntro"
        awaitItem().active shouldBe "app.b"
        finishedIn shouldBe listOf("a:done-r-a", "b:done-r-b")
      }
    }
  })
