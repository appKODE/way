package ru.kode.way.sample.compose.app.routing

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import ru.kode.way.Finish
import ru.kode.way.sample.compose.main.routing.MainFlowResult

// Lives in src/test (not per-flavor), so this same file is compiled and run once per flavor
// (testGoogleDebugUnitTest, testHuaweiDebugUnitTest) against that flavor's own generated
// AppChildFinishRequest/Finish types — proving both flavors' generated code is usable, not just
// that it compiles for one of them.
class AppFlowNodeTest :
  ShouldSpec({
    should("finish the app flow when the main flow finishes, regardless of flavor") {
      AppFlowNode().transition(AppChildFinishRequest.Main(MainFlowResult.Dismissed)) shouldBe Finish(Unit)
    }
  })
