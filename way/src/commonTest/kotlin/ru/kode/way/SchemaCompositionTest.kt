package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
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

class SchemaCompositionTest : ShouldSpec() {
  init {

    should("properly resolve targets with one flow composed into multiple flows as a child") {
      val schema = MCAppFlowSchema(
        loginFlowSchema = MCLoginFlowSchema(),
        mainFlowSchema = MCMainFlowSchema(
          loginFlowSchema = MCLoginFlowSchema()
        )
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
              tr(on = "B", Target.mainFlow.loginFlow(section = 55)),
              tr<MainFlowChildFinishRequest.LoginFlow>(Finish(Unit)),
            ),
            mainLoginFlowTransitions = listOf(
              tr(on = "C", NavigateTo(Target.loginFlow.credentials)),
              tr(on = "D", Finish(Unit))
            ),
          ),
          schema = schema
        ),
        onFinishRequest = { navServiceFinished = true; Ignore }
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
  }
}
