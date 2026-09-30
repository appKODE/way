package ru.kode.way

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
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

class FlowEventSinkTest :
  ShouldSpec({
    // app -> page1 -> login -> credentials -> permissions -> intro. "E" enters login, "P" opens permissions,
    // "C" is handled by credentials, "L" finishes login, which brings app back to page1
    class Fixture {
      val consulted = mutableListOf<String>()
      val dropped = mutableListOf<DropReason>()
      val states = mutableListOf<NavigationState>()
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

            override fun createPage1Node() = TestScreenNode()
            override fun createPage2Node() = TestScreenNode()
            override fun createLoginNodeBuilder(): NodeBuilder = LoginNodeBuilder(
              object : LoginNodeBuilder.Factory {
                override fun createRootNode() = TestFlowNodeWithResult(
                  initialTarget = Target.login.credentials,
                  dismissResult = 0,
                  transitions = listOf(tr("P", Target.login.permissions), tr("L", Finish(7))),
                )

                override fun createCredentialsNode() = TestScreenNode(transitions = listOf(trs("C", Stay)))
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
        sut.addNodeExtensionPoint(TestNodeExtensionPoint(preTransition = { _, path, _ -> consulted.add("$path") }))
        sut.addServiceExtensionPoint(
          object : ServiceExtensionPoint<Unit> {
            override fun onPreTransition(service: NavigationService<Unit>, event: Event, state: NavigationState) = Unit

            override fun onPostTransition(service: NavigationService<Unit>, event: Event, state: NavigationState) = Unit

            override fun onEventDropped(service: NavigationService<Unit>, event: Event, reason: DropReason) {
              dropped.add(reason)
            }
          },
        )
        sut.start()
      }

      val active get() = states.last().regions.values.single().active

      fun enterLogin(): Path {
        sut.sendEvent(TestEvent("E"))
        consulted.clear()
        return active.dropLast(1)
      }
    }

    should("let the active child screen handle an event sent through a flow sink") {
      val f = Fixture()
      val login = f.enterLogin()

      f.sut.eventSink(login).send(TestEvent("C"))

      f.dropped.shouldBeEmpty()
      f.consulted shouldBe listOf("app.page1.login.credentials")
    }

    should("bubble an event ignored by the active child up to the flow of the sink") {
      val f = Fixture()
      val login = f.enterLogin()

      f.sut.eventSink(login).send(TestEvent("L"))

      f.dropped.shouldBeEmpty()
      f.consulted.take(2) shouldBe listOf("app.page1.login.credentials", "app.page1.login")
      f.active.toString() shouldBe "app.page1"
    }

    should("keep a flow sink valid after its child changed and make it stale once the flow was recreated") {
      val f = Fixture()
      val login = f.enterLogin()
      val sink = f.sut.eventSink(login)
      f.sut.sendEvent(TestEvent("P"))
      f.active.toString() shouldBe "app.page1.login.credentials.permissions.intro"
      f.consulted.clear()

      sink.send(TestEvent("L"))

      f.dropped.shouldBeEmpty()
      f.consulted.first() shouldBe "app.page1.login.credentials.permissions.intro"
      f.active.toString() shouldBe "app.page1"

      sink.send(TestEvent("L"))

      f.dropped shouldBe listOf(DropReason.StaleSource(login))

      f.enterLogin()
      sink.send(TestEvent("L"))

      f.dropped shouldBe listOf(DropReason.StaleSource(login), DropReason.StaleSource(login))
      f.active.toString() shouldBe "app.page1.login.credentials"
    }

    should("resolve Back sent through a flow sink from the active leaf under the flow, like sendEvent") {
      val viaSink = Fixture()
      val direct = Fixture()
      val login = viaSink.enterLogin()
      direct.enterLogin()
      viaSink.sut.sendEvent(TestEvent("P"))
      direct.sut.sendEvent(TestEvent("P"))
      viaSink.consulted.clear()
      direct.consulted.clear()

      viaSink.sut.eventSink(login).send(Event.Back)
      direct.sut.sendEvent(Event.Back)

      viaSink.dropped.shouldBeEmpty()
      viaSink.consulted.first() shouldBe "app.page1.login.credentials.permissions.intro"
      viaSink.consulted shouldBe direct.consulted
      viaSink.active shouldBe direct.active
    }

    should("resolve an event sent through the root sink exactly like sendEvent") {
      val viaSink = Fixture()
      val direct = Fixture()
      viaSink.enterLogin()
      direct.enterLogin()

      viaSink.sut.eventSink(viaSink.active.take(1)).send(TestEvent("L"))
      direct.sut.sendEvent(TestEvent("L"))

      viaSink.consulted shouldBe direct.consulted
      viaSink.states.map { it.regions.values.single().active } shouldBe
        direct.states.map { it.regions.values.single().active }
    }
  })
