package ru.kode.way.compose

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.kode.way.DropReason
import ru.kode.way.Path

@OptIn(ExperimentalAnimationApi::class)
@RunWith(RobolectricTestRunner::class)
class EventSinkTest {
  @get:Rule
  val rule = createComposeRule()

  private fun FlowFixture.host(): FlowFixture = also { rule.setContent { NodeHost(service) } }

  private fun FlowFixture.send(name: String) {
    rule.runOnIdle { service.sendEvent(TestEvent(name)) }
    rule.waitForIdle()
  }

  @Test
  fun `sink stays the same instance across recompositions of a node`() {
    val fixture = FlowFixture().host()
    val main = rule.runOnIdle { fixture.screen("main") }

    rule.runOnIdle { main.tick.intValue++ }
    rule.waitForIdle()

    assertTrue("expected a recomposition, sinks=${main.sinks.size}", main.sinks.size >= 2)
    main.sinks.forEach { assertSame(main.sinks.first(), it) }
  }

  @Test
  fun `sink changes when node at same path is re-targeted with another argument`() {
    val fixture = FlowFixture().host()
    fixture.send("D")
    val oldSink = fixture.screen("details", "a").sinks.last()

    fixture.send("R")

    val newDetails = fixture.screen("details", "b")
    assertTrue("re-targeted details node was never rendered", newDetails.sinks.isNotEmpty())
    assertNotSame(oldSink, newDetails.sinks.last())
  }

  @Test
  fun `sink changes when node at same path is exited and entered again`() {
    val fixture = FlowFixture().host()
    fixture.send("D")
    val firstDetails = fixture.screen("details", "a")

    fixture.send("M")
    fixture.send("D")

    val secondDetails = fixture.screen("details", "a")
    assertNotSame(firstDetails, secondDetails)
    assertNotSame(firstDetails.sinks.last(), secondDetails.sinks.last())
  }

  @Test
  fun `sink changes and is bound to new service when service is replaced`() {
    val first = FlowFixture()
    val second = FlowFixture()
    var service by mutableStateOf(first.service)
    rule.setContent { NodeHost(service) }
    val oldSink = rule.runOnIdle { first.screen("main").sinks.last() }

    rule.runOnIdle { service = second.service }
    rule.waitForIdle()

    val newMain = second.screen("main")
    assertTrue("main node of the new service was never rendered", newMain.sinks.isNotEmpty())
    assertNotSame(oldSink, newMain.sinks.last())
    rule.runOnIdle { newMain.sinks.last().send(TestEvent("D")) }
    assertEquals(emptyList<Any>(), second.dropped)
    assertEquals(FlowFixture.detailsPath, second.state?.activePath)
    assertEquals(Path("app", "main"), first.state?.activePath)
  }

  @Test
  fun `event sent through stale sink is dropped as StaleSource without throwing in strict mode`() {
    val fixture = FlowFixture().host()
    fixture.send("D")
    val staleSink = fixture.screen("details", "a").sinks.last()
    fixture.send("M")

    rule.runOnIdle { staleSink.send(TestEvent("D")) }

    assertTrue(fixture.service.strictEventDropping)
    assertEquals(listOf(TestEvent("D") to DropReason.StaleSource(FlowFixture.detailsPath)), fixture.dropped)
    assertEquals(Path("app", "main"), fixture.state?.activePath)
  }

  @Test
  fun `reading LocalEventSink outside NodeHost fails with explanatory message`() {
    val error = assertThrows(IllegalStateException::class.java) {
      rule.setContent { LocalEventSink.current }
    }

    assertEquals("no EventSink provided — read LocalEventSink inside a node rendered by NodeHost", error.message)
  }

  @Test
  fun `each region of parallel node gets its own sink`() {
    val fixture = ParallelFixture()
    rule.setContent { NodeHost(fixture.service) }

    val alphaSink = rule.runOnIdle { fixture.screen("alphaScreen").sinks.last() }
    val betaSink = fixture.screen("betaScreen").sinks.last()

    assertNotSame(alphaSink, betaSink)
  }

  @Test
  fun `event sent through region sink is handled only in that region`() {
    val fixture = ParallelFixture()
    rule.setContent { NodeHost(fixture.service) }
    val alphaSink = rule.runOnIdle { fixture.screen("alphaScreen").sinks.last() }

    rule.runOnIdle { alphaSink.send(TestEvent("next")) }
    rule.waitForIdle()

    val activePaths = fixture.state!!.regions.values.map { it.active }
    assertEquals(listOf(Path("tabs", "alpha", "alphaNext"), Path("tabs", "beta", "betaScreen")), activePaths)
    assertTrue(fixture.screens.none { it.name == "betaNext" })
    assertTrue(fixture.dropped.isEmpty())
  }

  @Test
  fun `event sent through parallel node sink is handled by the active children of its regions`() {
    val fixture = ParallelFixture()
    rule.setContent { NodeHost(fixture.service) }
    val tabsSink = rule.runOnIdle { fixture.tabs.sinks.last() }

    rule.runOnIdle { tabsSink.send(TestEvent("next")) }
    rule.waitForIdle()

    val activePaths = fixture.state!!.regions.values.map { it.active }
    assertEquals(listOf(Path("tabs", "alpha", "alphaNext"), Path("tabs", "beta", "betaNext")), activePaths)
    assertTrue(fixture.dropped.isEmpty())
  }

  @Test
  fun `event sent through screen sink is delivered and navigates`() {
    val fixture = FlowFixture().host()
    val mainSink = rule.runOnIdle { fixture.screen("main").sinks.last() }

    rule.runOnIdle { mainSink.send(TestEvent("D")) }
    rule.waitForIdle()

    assertEquals(FlowFixture.detailsPath, fixture.state?.activePath)
    assertTrue(fixture.dropped.isEmpty())
    assertEquals(1, fixture.screen("details", "a").sinks.distinct().size)
  }
}
