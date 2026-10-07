package ru.kode.way.sample.compose.app.routing

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import ru.kode.way.Target

// Target.app.login only exists because google's schema extension (src/google/way/app-flow.dot) adds
// a "login" node — a huawei test referencing it would fail to compile, which is itself part of the
// proof that a flavor's dot file produces a genuinely different generated schema.
class AppFlowNodeGoogleTest :
  ShouldSpec({
    should("start at login in the google flavor's app flow") {
      AppFlowNode().initial shouldBe Target.app.login
    }
  })
