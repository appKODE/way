package ru.kode.way.compose

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.kode.way.Event
import ru.kode.way.FlowTransition
import ru.kode.way.Ignore
import ru.kode.way.NavigationService
import ru.kode.way.Node
import ru.kode.way.ParallelFlowNode
import ru.kode.way.Path
import ru.kode.way.RegionId
import ru.kode.way.Schema
import ru.kode.way.ScreenNode
import ru.kode.way.ScreenTarget
import ru.kode.way.ScreenTransition
import ru.kode.way.Segment

@OptIn(ExperimentalAnimationApi::class)
@RunWith(RobolectricTestRunner::class)
class FocusedRegionHostTest {
  @get:Rule
  val rule = createComposeRule()

  private var regionRoots: List<Path> = emptyList()

  private val service = NavigationService<Unit>(
    TestNodeBuilder(
      TestSchema(
        "tabs",
        mapOf(
          "alpha" to Schema.NodeType.Flow,
          "alpha.screen" to Schema.NodeType.Screen,
          "beta" to Schema.NodeType.Flow,
          "beta.screen" to Schema.NodeType.Screen,
        ),
        subRegions = listOf("alpha", "beta"),
        childSchemas = listOf("alpha", "beta").associate {
          Segment(it) to TestSchema(it, mapOf("screen" to Schema.NodeType.Screen))
        },
      ),
    ) { path, _ ->
      val segments = path.split('.')
      when (segments.size) {
        1 -> TabsNode()
        2 -> TestFlowNode(initial = ScreenTarget(Path("screen")), transitions = emptyMap())
        else -> CounterScreen(segments[1])
      }
    },
    onFinishRequest = { Ignore },
  ).apply { addTransitionListener { state -> regionRoots = state.regions.keys.map { it.path } } }

  private fun focus(region: String) {
    rule.runOnIdle { service.send(TestEvent(region)) }
    rule.waitForIdle()
  }

  @Test
  fun `only the focused region is rendered and a region starts when it gets focus`() {
    rule.setContent { NodeHost(service) }

    rule.onNodeWithText("alpha 0").assertExists()
    rule.onNodeWithText("beta 0").assertDoesNotExist()
    assertEquals(listOf(Path("tabs", "alpha")), regionRoots)

    focus("beta")

    rule.onNodeWithText("beta 0").assertExists()
    rule.onNodeWithText("alpha 0").assertDoesNotExist()
    assertEquals(listOf(Path("tabs", "alpha"), Path("tabs", "beta")), regionRoots)
  }

  @Test
  fun `saved state of a region survives losing focus`() {
    rule.setContent { NodeHost(service) }
    rule.onNodeWithText("alpha 0").performClick()
    rule.onNodeWithText("alpha 1").assertExists()

    focus("beta")
    focus("alpha")

    rule.onNodeWithText("alpha 1").assertExists()
  }

  private class TabsNode :
    ParallelFlowNode<Unit>(),
    ComposableNode {
    private var focused by mutableStateOf(regionId("alpha"))
    override val dismissResult = Unit
    override val initialRegions get() = setOf(regionId("alpha"))

    override fun transition(event: Event): FlowTransition<Unit> {
      val regionId = (event as? TestEvent)?.let { regionId(it.name) } ?: return Ignore
      focused = regionId
      return startRegion(regionId)
    }

    @Composable
    override fun Content(modifier: Modifier) {
      FocusedRegionHost(focused, modifier)
    }

    private fun regionId(name: String) = RegionId(Path("tabs", name))
  }

  private class CounterScreen(private val name: String) :
    ScreenNode,
    ComposableNode {
    override fun transition(event: Event): ScreenTransition = Ignore

    @Composable
    override fun Content(modifier: Modifier) {
      var count by rememberSaveable { mutableIntStateOf(0) }
      BasicText("$name $count", modifier.clickable { count++ })
    }
  }
}
