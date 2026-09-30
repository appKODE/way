package ru.kode.way

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import ru.kode.way.extension.node.hook.BaseFlowNode
import ru.kode.way.extension.node.hook.BaseScreenNode
import ru.kode.way.nav10.AppChildFinishRequest
import ru.kode.way.nav10.AppNodeBuilder
import ru.kode.way.nav10.LoginNodeBuilder
import ru.kode.way.nav10.NavService10LoginSchema
import ru.kode.way.nav10.NavService10PermissionsSchema
import ru.kode.way.nav10.NavService10Schema
import ru.kode.way.nav10.PermissionsNodeBuilder
import ru.kode.way.nav10.app
import ru.kode.way.nav10.login
import ru.kode.way.nav10.permissions

class NodeEventSinkTest :
  ShouldSpec({
    // app -> page1 -> login -> credentials -> permissions -> intro. "E" enters login, "P" opens permissions,
    // "L" finishes login, which brings app back to page1. Page1 and login are Base*Node, recorded when entered.
    class Fixture(private val loginOnEntry: (EventSink) -> Unit = {}) {
      val dropped = mutableListOf<Pair<Event, DropReason>>()
      val states = mutableListOf<NavigationState>()
      val page1Nodes = mutableListOf<BaseScreenNode>()
      val loginNodes = mutableListOf<BaseFlowNode<Int>>()
      val sinksSeenOnEntry = mutableListOf<EventSink>()

      val sut = NavigationService(
        AppNodeBuilder(
          object : AppNodeBuilder.Factory {
            override fun createRootNode() = TestFlowNode(
              initialTarget = Target.app.page1,
              transitions = listOf(
                tr("E", Target.app.login),
                tr<AppChildFinishRequest.Login>(NavigateTo(Target.app.page1)),
              ),
            )

            override fun createPage1Node() = object : BaseScreenNode() {
              override fun onEntry(event: Event) {
                sinksSeenOnEntry.add(eventSink)
                page1Nodes.add(this)
              }
            }

            override fun createPage2Node() = TestScreenNode()
            override fun createLoginNodeBuilder(): NodeBuilder = LoginNodeBuilder(
              object : LoginNodeBuilder.Factory {
                override fun createRootNode() = object : BaseFlowNode<Int>() {
                  override val initial: Target = Target.login.credentials
                  override val dismissResult: Int = 0

                  override fun onEntry(event: Event) {
                    sinksSeenOnEntry.add(eventSink)
                    loginNodes.add(this)
                    loginOnEntry(eventSink)
                  }

                  override fun transition(event: Event): FlowTransition<Int> = when (event) {
                    TestEvent("P") -> NavigateTo(Target.login.permissions)
                    TestEvent("L") -> Finish(7)
                    else -> Ignore
                  }
                }

                override fun createCredentialsNode() = TestScreenNode()
                override fun createPermissionsNodeBuilder(): NodeBuilder = PermissionsNodeBuilder(
                  object : PermissionsNodeBuilder.Factory {
                    override fun createRootNode() = TestFlowNodeWithResult(Target.permissions.intro, "")
                    override fun createIntroNode() = TestScreenNode()
                  },
                  NavService10PermissionsSchema(),
                )
              },
              NavService10LoginSchema(NavService10PermissionsSchema()),
            )
          },
          NavService10Schema(NavService10LoginSchema(NavService10PermissionsSchema())),
        ),
        onFinishRequest = { _: Unit -> Ignore },
      )

      init {
        sut.addTransitionListener { states.add(it) }
        sut.addServiceExtensionPoint(
          object : ServiceExtensionPoint<Unit> {
            override fun onPreTransition(service: NavigationService<Unit>, event: Event, state: NavigationState) = Unit

            override fun onPostTransition(service: NavigationService<Unit>, event: Event, state: NavigationState) = Unit

            override fun onEventDropped(service: NavigationService<Unit>, event: Event, reason: DropReason) {
              dropped.add(event to reason)
            }
          },
        )
        sut.start()
      }

      val active get() = states.last().regions.values.single().active.toString()
    }

    should("be an EventSink whose send resolves events from the whole tree") {
      val f = Fixture()
      val sink: EventSink = f.sut

      sink.send(TestEvent("E"))

      f.dropped.shouldBeEmpty()
      f.active shouldBe "app.page1.login.credentials"
    }

    should("attach a usable eventSink to BaseScreenNode and BaseFlowNode before onEntry") {
      val f = Fixture()
      f.sut.send(TestEvent("E"))

      f.sinksSeenOnEntry.size shouldBe 2
      f.sinksSeenOnEntry[0] shouldBeSameInstanceAs f.page1Nodes.single().eventSink
      f.sinksSeenOnEntry[1] shouldBeSameInstanceAs f.loginNodes.single().eventSink
    }

    should("apply an event sent through a flow node's eventSink from its onEntry after the current dispatch") {
      val f = Fixture(loginOnEntry = { it.send(TestEvent("P")) })

      f.sut.send(TestEvent("E"))

      f.dropped.shouldBeEmpty()
      f.active shouldBe "app.page1.login.credentials.permissions.intro"
    }

    should("apply a deferred send through a flow node's eventSink while the flow is still alive") {
      val f = Fixture()
      f.sut.send(TestEvent("E"))
      val deferred = f.loginNodes.single().eventSink
      f.sut.send(TestEvent("P"))

      deferred.send(TestEvent("L"))

      f.dropped.shouldBeEmpty()
      f.active shouldBe "app.page1"
    }

    should("drop an event sent through a node's eventSink after the node exited as StaleSource") {
      val f = Fixture()
      f.sut.send(TestEvent("E"))
      val login = f.loginNodes.single()
      f.sut.send(TestEvent("L"))
      val transitions = f.states.size

      login.eventSink.send(TestEvent("P"))

      f.dropped shouldBe listOf(TestEvent("P") to DropReason.StaleSource(login.nodePath))
      f.states.size shouldBe transitions
      f.active shouldBe "app.page1"
    }

    should("give a re-entered node position a fresh eventSink while the old one stays stale") {
      val f = Fixture()
      f.sut.send(TestEvent("E"))
      f.sut.send(TestEvent("L"))
      f.sut.send(TestEvent("E"))
      val (first, second) = f.loginNodes

      first.eventSink.send(TestEvent("P"))
      f.dropped.size shouldBe 1
      second.eventSink.send(TestEvent("P"))

      f.dropped.size shouldBe 1
      f.active shouldBe "app.page1.login.credentials.permissions.intro"
    }

    should("throw when eventSink is read before the node was entered") {
      shouldThrow<IllegalStateException> { object : BaseScreenNode() {}.eventSink }
        .message shouldContain "before the runtime calls onEntry"
      shouldThrow<IllegalStateException> {
        object : BaseFlowNode<Unit>() {
          override val initial: Target = Target.app.page1
          override val dismissResult = Unit
        }.eventSink
      }.message shouldContain "before the runtime calls onEntry"
    }
  })
