package ru.kode.way.compose

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import ru.kode.way.DropReason
import ru.kode.way.Event
import ru.kode.way.EventSink
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.Ignore
import ru.kode.way.NavigateTo
import ru.kode.way.NavigationService
import ru.kode.way.NavigationState
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.ParallelFlowNode
import ru.kode.way.Path
import ru.kode.way.RegionId
import ru.kode.way.Schema
import ru.kode.way.ScreenNode
import ru.kode.way.ScreenTarget
import ru.kode.way.ScreenTransition
import ru.kode.way.Segment
import ru.kode.way.ServiceExtensionPoint
import ru.kode.way.Target

data class TestEvent(val name: String) : Event

/**
 * A hand-rolled [Schema] (the way plugin generates none for Android unit tests). [nodes] are dot-separated paths
 * below [root]; a non-empty [subRegions] makes [root] a parallel flow with one region per name.
 */
class TestSchema(
  root: String,
  private val nodes: Map<String, Schema.NodeType>,
  subRegions: List<String> = emptyList(),
  override val childSchemas: Map<Segment, Schema> = emptyMap(),
  private val parameterized: Set<String> = emptySet(),
) : Schema {
  override val rootSegment = Segment(root)
  private val rootType = if (subRegions.isEmpty()) Schema.NodeType.Flow else Schema.NodeType.ParallelFlow
  override val regions = subRegions.map { RegionId(Path(root, it)) }.ifEmpty { listOf(RegionId(Path(root))) }

  override fun target(regionId: RegionId, segment: Segment, rootSegmentAlias: Segment?): Path? {
    val rootSegment = rootSegmentAlias ?: rootSegment
    if (segment == this.rootSegment) return Path(rootSegment)
    val relative = nodes.keys.find { it.substringAfterLast('.') == segment.id } ?: return null
    return Path(listOf(rootSegment) + relative.split('.').map(::Segment))
  }

  override fun nodeType(regionId: RegionId, path: Path, rootSegmentAlias: Segment?): Schema.NodeType =
    if (path.length == 1) rootType else nodes.getValue(path.relative())

  override fun isParameterized(regionId: RegionId, path: Path, rootSegmentAlias: Segment?) =
    path.length > 1 && path.relative() in parameterized

  override fun createChildFlowFinishRequestEvent(regionId: RegionId, path: Path, result: Any): Event =
    error("no child flow finishes in tests")

  private fun Path.relative() = segments.drop(1).joinToString(".") { it.id }
}

/** Builds nodes by their dot-separated path (e.g. `app.main.details`), passing the node's own payload. */
class TestNodeBuilder(override val schema: Schema, private val build: (path: String, payload: Any?) -> Node) :
  NodeBuilder {
  override fun build(path: Path, payloads: Map<Path, Any>, rootSegmentAlias: Segment?): Node =
    build(path.toString(), payloads[path])

  override fun invalidateCache(alivePaths: Set<Path>) = Unit
}

class TestFlowNode(override val initial: Target, private val transitions: Map<String, FlowTransition<Unit>>) :
  FlowNode<Unit> {
  override val dismissResult = Unit
  override fun transition(event: Event): FlowTransition<Unit> = transitions[(event as? TestEvent)?.name] ?: Ignore
}

/** A screen which records the [LocalEventSink] it sees on every composition. Bump [tick] to force a recomposition. */
class SinkRecordingScreen(val name: String, val payload: Any? = null) :
  ScreenNode,
  ComposableNode {
  val sinks = mutableListOf<EventSink>()
  val tick = mutableIntStateOf(0)

  override fun transition(event: Event): ScreenTransition = Ignore

  @Composable
  override fun Content(modifier: Modifier) {
    val sink = LocalEventSink.current
    SideEffect { sinks += sink }
    BasicText("$name $payload ${tick.intValue}", modifier)
  }
}

private fun screenTarget(vararg segments: String, payload: Any? = null) =
  ScreenTarget(Path(segments.first(), *segments.drop(1).toTypedArray()), payload)

abstract class Fixture(schema: Schema) {
  val screens = mutableListOf<SinkRecordingScreen>()
  val dropped = mutableListOf<Pair<Event, DropReason>>()
  var state: NavigationState? = null

  val service = NavigationService<Unit>(TestNodeBuilder(schema, ::buildNode), onFinishRequest = { Ignore }).apply {
    strictEventDropping = true
    addTransitionListener { state = it }
    addServiceExtensionPoint(
      object : ServiceExtensionPoint<Unit> {
        override fun onPreTransition(service: NavigationService<Unit>, event: Event, state: NavigationState) = Unit
        override fun onPostTransition(service: NavigationService<Unit>, event: Event, state: NavigationState) = Unit
        override fun onEventDropped(service: NavigationService<Unit>, event: Event, reason: DropReason) {
          dropped += event to reason
        }
      },
    )
  }

  fun screen(name: String, payload: Any? = null) = screens.last { it.name == name && it.payload == payload }

  /** Builds the node at the dot-separated [path] (e.g. `app.main.details`) with its own [payload]. */
  protected abstract fun buildNode(path: String, payload: Any?): Node

  protected fun record(node: SinkRecordingScreen) = node.also { screens += it }
}

/**
 * `app` flow: `main` -> `main.details(id)`. "D" opens details("a"), "R" re-targets it to details("b"), "M" goes back
 * to main.
 */
class FlowFixture :
  Fixture(
    TestSchema(
      "app",
      mapOf("main" to Schema.NodeType.Screen, "main.details" to Schema.NodeType.Screen),
      parameterized = setOf("main.details"),
    ),
  ) {
  override fun buildNode(path: String, payload: Any?): Node = when (path) {
    "app" -> TestFlowNode(
      initial = screenTarget("main"),
      transitions = mapOf(
        "D" to NavigateTo(screenTarget("main", "details", payload = "a")),
        "R" to NavigateTo(screenTarget("main", "details", payload = "b")),
        "M" to NavigateTo(screenTarget("main")),
      ),
    )

    "app.main" -> record(SinkRecordingScreen("main"))

    "app.main.details" -> record(SinkRecordingScreen("details", payload))

    else -> error("unknown $path")
  }

  companion object {
    val detailsPath = Path("app", "main", "details")
  }
}

/**
 * A parallel `tabs` root with `alpha` and `beta` regions, each a flow with `<region>Screen` which "next" replaces with
 * `<region>Next`. The root renders both regions via `NodeHost(regionId)`.
 */
class ParallelFixture :
  Fixture(
    TestSchema(
      "tabs",
      REGIONS.flatMap { listOf(it, "$it.${it}Screen", "$it.${it}Next") }
        .associateWith { if ('.' in it) Schema.NodeType.Screen else Schema.NodeType.Flow },
      subRegions = REGIONS,
      childSchemas = REGIONS.associate {
        Segment(it) to
          TestSchema(it, mapOf("${it}Screen" to Schema.NodeType.Screen, "${it}Next" to Schema.NodeType.Screen))
      },
    ),
  ) {
  lateinit var tabs: TabsNode

  override fun buildNode(path: String, payload: Any?): Node {
    val segments = path.split('.')
    return when (segments.size) {
      1 -> TabsNode().also { tabs = it }

      2 -> TestFlowNode(
        initial = screenTarget("${segments[1]}Screen"),
        transitions = mapOf("next" to NavigateTo(screenTarget("${segments[1]}Next"))),
      )

      else -> record(SinkRecordingScreen(segments.last()))
    }
  }

  class TabsNode :
    ParallelFlowNode<Unit>(),
    ComposableNode {
    val sinks = mutableListOf<EventSink>()
    override val dismissResult = Unit
    override fun transition(event: Event): FlowTransition<Unit> = Ignore

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    override fun Content(modifier: Modifier) {
      val sink = LocalEventSink.current
      SideEffect { sinks += sink }
      Column(modifier) {
        REGIONS.forEach { NodeHost(RegionId(Path("tabs", it))) }
      }
    }
  }

  private companion object {
    val REGIONS = listOf("alpha", "beta")
  }
}

val NavigationState.activePath: Path get() = regions.values.single().active
