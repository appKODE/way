package ru.kode.way.sample.compose.app.routing

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import ru.kode.way.Target

// Target.app.login only exists because google's app-flow.dot (src/main/way) declares a "login"
// node — a huawei test referencing it would fail to compile, which is itself part of the proof
// that flavor dot-file overrides produce genuinely different generated schemas.
class AppFlowNodeGoogleTest :
  ShouldSpec({
    should("start at login in the google flavor's app flow") {
      AppFlowNode().initial shouldBe Target.app.login
    }
  })
