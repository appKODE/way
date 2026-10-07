package ru.kode.way.compose

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.ref.WeakReference

@OptIn(ExperimentalAnimationApi::class)
@RunWith(RobolectricTestRunner::class)
class NodeRetentionTest {
  @get:Rule
  val rule = createComposeRule()

  @Test
  fun `node which was active when the host was composed is released after it exits`() {
    val fixture = FlowFixture()
    fixture.service.start()
    fixture.service.send(TestEvent("D"))
    rule.setContent { NodeHost(fixture.service) }
    val exited = rule.runOnIdle { WeakReference(fixture.screen("details", "a")) }

    rule.runOnIdle { fixture.service.send(TestEvent("R")) }
    rule.waitForIdle()
    fixture.screens.clear()

    for (attempt in 1..10) {
      if (exited.get() == null) break
      System.gc()
      Thread.sleep(50)
    }
    assertNull(exited.get())
  }
}
