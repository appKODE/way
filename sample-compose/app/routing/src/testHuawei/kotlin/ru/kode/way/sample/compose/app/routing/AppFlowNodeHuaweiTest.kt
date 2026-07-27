package ru.kode.way.sample.compose.app.routing

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import ru.kode.way.Target

class AppFlowNodeHuaweiTest :
  ShouldSpec({
    should("skip the google-only login step and start at main in the huawei flavor's app flow") {
      AppFlowNode().initial shouldBe Target.app.main
    }
  })
