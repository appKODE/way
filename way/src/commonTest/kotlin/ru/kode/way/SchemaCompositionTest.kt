package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import ru.kode.way.fake.mc01.AppFlowNodeFactory
import ru.kode.way.mc01.AppFlowNodeBuilder
import ru.kode.way.mc01.MCAppFlowSchema
import ru.kode.way.mc01.MCLoginFlowSchema
import ru.kode.way.mc01.MCMainFlowSchema
import ru.kode.way.mc01.appFlow
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
      val sut = NavigationService<Unit>(
        schema = schema,
        nodeBuilder = AppFlowNodeBuilder(
          nodeFactory = AppFlowNodeFactory(
            initialTarget = Target.appFlow.loginFlow(section = 42, onFinishRequest = {
              NavigateTo(Target.appFlow.mainFlow(onFinishRequest = { Finish(Unit) }))
            }),
            appLoginFlowTransitions = listOf(
              tr(on = "A", Finish(Unit))
            ),
            mainFlowTransitions = listOf(
              tr(on = "B", Target.mainFlow.loginFlow(section = 55, onFinishRequest = { Finish(Unit) }))
            )
          ),
          schema = schema
        ),
        onFinishRequest = { Ignore }
      )

      sut.collectTransitions().test {
        awaitItem().active shouldBe "appFlow.loginFlow.credentials"
        sut.sendEvent(TestEvent("A"))
        awaitItem().active shouldBe "appFlow.mainFlow.main"
        sut.sendEvent(TestEvent("B"))
        awaitItem().active shouldBe "appFlow.mainFlow.main.loginFlow.credentials.otp"
      }
    }
  }
}
