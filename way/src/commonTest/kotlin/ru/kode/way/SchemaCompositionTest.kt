package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import ru.kode.way.fake.mc01.AppFlowNodeFactory
import ru.kode.way.mc01.AppFlowChildFinishRequest
import ru.kode.way.mc01.AppFlowNodeBuilder
import ru.kode.way.mc01.MCAppFlowSchema
import ru.kode.way.mc01.MCLoginFlowSchema
import ru.kode.way.mc01.MCMainFlowSchema
import ru.kode.way.mc01.MainFlowChildFinishRequest
import ru.kode.way.mc01.appFlow
import ru.kode.way.mc01.loginFlow
import ru.kode.way.mc01.mainFlow
import ru.kode.way.nav12.AppNodeBuilder
import ru.kode.way.nav12.LoginNodeBuilder
import ru.kode.way.nav12.NavService12LoginSchema
import ru.kode.way.nav12.NavService12Schema
import java.nio.charset.Charset
import ru.kode.way.nav12.AppChildFinishRequest as Nav12AppChildFinishRequest
import ru.kode.way.nav12.app as app12
import ru.kode.way.nav12.login as login12

class SchemaCompositionTest : ShouldSpec() {
  init {

    should("properly resolve targets with one flow composed into multiple flows as a child") {
      val schema = MCAppFlowSchema(
        loginFlowSchema = MCLoginFlowSchema(),
        mainFlowSchema = MCMainFlowSchema(
          loginFlowSchema = MCLoginFlowSchema(),
        ),
      )
      var navServiceFinished = false
      val sut = NavigationService<Unit>(
        nodeBuilder = AppFlowNodeBuilder(
          nodeFactory = AppFlowNodeFactory(
            initialTarget = Target.appFlow.loginFlow(section = 42),
            flowTransitions = listOf(
              tr<AppFlowChildFinishRequest.LoginFlow>(Target.appFlow.mainFlow),
              tr<AppFlowChildFinishRequest.MainFlow>(Finish(Unit)),
            ),
            appLoginFlowTransitions = listOf(
              tr(on = "A", Finish(Unit)),
            ),
            mainFlowTransitions = listOf(
              tr(on = "B", Target.mainFlow.loginFlow(count = 1, section = 55)),
              tr<MainFlowChildFinishRequest.LoginFlow>(Finish(Unit)),
            ),
            mainLoginFlowTransitions = listOf(
              tr(on = "C", NavigateTo(Target.loginFlow.credentials)),
              tr(on = "D", Finish(Unit)),
            ),
          ),
          schema = schema,
        ),
        onFinishRequest = {
          navServiceFinished = true
          Ignore
        },
      )

      sut.collectTransitions().test {
        awaitItem().active shouldBe "appFlow.loginFlow.credentials"
        sut.sendEvent(TestEvent("A"))
        awaitItem().active shouldBe "appFlow.loginFlow.credentials" // sends finish
        awaitItem().active shouldBe "appFlow.mainFlow.main" // after finish
        sut.sendEvent(TestEvent("B"))
        awaitItem().active shouldBe "appFlow.mainFlow.main.loginFlow.credentials.otp"
        sut.sendEvent(TestEvent("C"))
        awaitItem().active shouldBe "appFlow.mainFlow.main.loginFlow.credentials"
        sut.sendEvent(TestEvent("D"))
        awaitItem().active shouldBe "appFlow.mainFlow.main.loginFlow.credentials" // main.login sends finish
        awaitItem().active shouldBe "appFlow.mainFlow.main.loginFlow.credentials" // main sends finish
        awaitItem().active shouldBe "appFlow.mainFlow.main.loginFlow.credentials" // app sends finish
        awaitItem()
        navServiceFinished shouldBe true
      }
    }

    should("drop an event whose target misses the payload of a parameterized composed schema root") {
      // the composed schema hides its parameters, so the pre-check lets the transition through and the generated
      // AppNodeBuilder throws while building the login node builder: the backstop path
      val appSchema = NavService12Schema(NavService12LoginSchema())
      val hiddenAppSchema = object : Schema by appSchema {
        override val childSchemas = appSchema.childSchemas.mapValues { (_, child) ->
          object : Schema by child {
            override fun isParameterized(regionId: RegionId, path: Path, rootSegmentAlias: Segment?) = false
          }
        }
      }
      // the login flow while page1 is alive, but without a payload for the login schema root
      val loginWithoutPayload = FlowTarget(Target.app12.login(defaultUserName = "unused").path)
      val nodeBuilder = AppNodeBuilder(
        object : AppNodeBuilder.Factory {
          override fun createRootNode(timeout: Int) = TestFlowNode(
            initialTarget = Target.app12.page1(Charsets.UTF_32),
            payload = timeout,
            transitions = listOf(
              tr("C", loginWithoutPayload),
              tr("P2", Target.app12.page2),
              tr<Nav12AppChildFinishRequest.Login>(Stay),
            ),
          )

          override fun createPage2Node() = TestScreenNode()
          override fun createPage1Node(charset: Charset) = TestScreenNode(payload = charset)

          override fun createLoginNodeBuilder(defaultUserName: String): NodeBuilder = LoginNodeBuilder(
            object : LoginNodeBuilder.Factory {
              override fun createRootNode(defaultUserName: String) = TestFlowNode(
                initialTarget = Target.login12.credentials(defaultPhone = "+7981123456"),
                payload = defaultUserName,
              )

              override fun createCredentialsNode(defaultPhone: String) = TestScreenNode(payload = defaultPhone)
              override fun createOtpNode(useAnimation: Boolean) = TestScreenNode(payload = useAnimation)
            },
            NavService12LoginSchema(),
          )
        },
        appSchema,
      )
      val sut = NavigationService(
        object : NodeBuilder by nodeBuilder {
          override val schema: Schema = hiddenAppSchema
        },
        onFinishRequest = { _: Int -> Ignore },
      )
      val states = mutableListOf<NavigationState>()
      val lifecycle = mutableListOf<String>()
      val dropped = mutableListOf<DropReason>()
      sut.addTransitionListener { states.add(it) }
      sut.addNodeExtensionPoint(
        TestNodeExtensionPoint(
          preEntry = { _, path -> lifecycle.add("entry $path") },
          preExit = { _, path -> lifecycle.add("exit $path") },
        ),
      )
      sut.addServiceExtensionPoint(
        object : ServiceExtensionPoint<Int> {
          override fun onPreTransition(service: NavigationService<Int>, event: Event, state: NavigationState) = Unit
          override fun onPostTransition(service: NavigationService<Int>, event: Event, state: NavigationState) = Unit
          override fun onEventDropped(service: NavigationService<Int>, event: Event, reason: DropReason) {
            dropped.add(reason)
          }
        },
      )
      sut.start(42)
      states.last().active shouldBe "app.page1"
      states.clear()
      lifecycle.clear()

      sut.sendEvent(TestEvent("C"))

      (dropped.single() as DropReason.MissingPayload).path.toString() shouldBe "app.page1.login"
      states.shouldBeEmpty()
      // the rollback compensates every exit with an entry
      lifecycle.count { it.startsWith("exit") } shouldBe lifecycle.count { it.startsWith("entry") }

      // the service is still usable
      sut.sendEvent(TestEvent("P2"))
      states.single().active shouldBe "app.page2"
    }
  }
}
