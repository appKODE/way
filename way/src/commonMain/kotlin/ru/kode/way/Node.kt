package ru.kode.way

sealed interface Node {
  /**
   * Called when the runtime first activates this node. Default impl delegates to the
   * [onEntry] overload without the path; override the [path] variant when the node needs to
   * know its own absolute mount point (e.g. to translate schema-local sibling RegionIds to
   * the absolute paths used by `NavigationState.regions`).
   *
   * The [path] is the absolute path from the root [FlowNode] down to this node, including
   * this node's own segment as the last element. It remains stable for the lifetime of the
   * node (until the matching [onExit] / [onDispose]).
   */
  fun onEntry(event: Event, path: Path) = onEntry(event)
  fun onEntry(event: Event) = Unit
  fun onExit(event: Event, path: Path) = onExit(event)
  fun onExit(event: Event) = Unit

  // No path variant: dispose is path-independent teardown (release scopes/subscriptions), so unlike
  // onEntry/onExit it does not need the node's mount point.
  fun onDispose() = Unit
}

/**
 * A node that owns a navigation sub-graph (flow).
 *
 * @param R The type of result this flow produces when it finishes via [Finish].
 *
 * [initial] designates the first target the runtime navigates to when this flow is entered.
 * It may point to a [ScreenNode] sibling, a child [FlowNode], or a [ParallelFlowNode].
 *
 * [dismissResult] is the value used by the runtime as the implicit [Finish] result when the user
 * presses Back from this flow's root screen. It is also used when a [ParallelFlowNode] containing
 * this flow is exited. Choose a value that represents "no meaningful result" for your use case.
 *
 * [transition] is called for every [Event] that is not consumed by a deeper node in this flow's
 * sub-graph. Return a [FlowTransition] to drive navigation:
 * - [NavigateTo] — navigate to another node
 * - [Finish] — finish this flow with a result
 * - [Stay] — consume the event and remain on the current screen
 * - [Ignore] — pass the event up to the parent node
 * - [EnqueueEvent] — schedule an event to be processed after the current transition completes
 */
interface FlowNode<R : Any> : Node {
  val initial: Target
  val dismissResult: R
  fun transition(event: Event): FlowTransition<R>
}

/**
 * A node that displays a single screen.
 *
 * [transition] is called for every [Event] delivered to this node. The node is the first to
 * receive each event; if it returns [Ignore] the event bubbles up to the parent [FlowNode] or
 * [ParallelFlowNode].
 *
 * Return a [ScreenTransition] to drive navigation:
 * - [NavigateTo] — navigate to a sibling node within the same parent flow schema
 * - [Stay] — consume the event and remain on this screen
 * - [Ignore] — pass the event up to the parent node
 * - [EnqueueEvent] — schedule an event to be processed after the current transition completes
 */
interface ScreenNode : Node {
  fun transition(event: Event): ScreenTransition
}

/**
 * A flow whose body coexists in parallel sub-regions.
 *
 * Inherits the flow's lifecycle ([dismissResult], [Finish], parent-finish routing) AND adds the
 * structural layout of multiple navigation regions that exist simultaneously (e.g. tabs, drawer
 * + content, main + sheet overlay).
 *
 * Use cases:
 * - The schema's top-level root is a layered "app shell" of head + sheet.
 * - A flow's body is a tab bar — the flow IS the tab container, not a separate wrapper.
 * - Drawer apps where the drawer state and main content share lifecycle.
 *
 * For a regular flow with linear navigation, use [FlowNode] instead. For a single screen, use
 * [ScreenNode].
 *
 * ## Presentation is the app's job
 *
 * The library stores no "focused"/"visible" sub-region state — which sub-region is currently shown
 * (the selected tab/panel) is presentation, owned entirely by the app. A subclass that needs it
 * simply holds its own field (e.g. a `MutableStateFlow` for a UI-framework-free node class, or a
 * Compose `mutableStateOf`) and reads it when rendering.
 *
 * Rendering itself attaches through the UI module's opt-in interface, exactly like screens: in
 * `way-compose` the subclass implements `ComposableNode` and its `Content()` lays out the parallel
 * (tab bar, panes, …) hosting each sub-region. Note there is no parallel-level `initial` — each
 * sub-region is a flow that supplies its own [FlowNode.initial]; to start on a different default
 * tab, seed your own presentation field with that sub-region's `RegionId`.
 *
 * ## Transitions
 *
 * [transition] is called for every [Event] delivered to this parallel-flow node after active
 * screen nodes in all sub-regions have had a chance to handle it. Return a [FlowTransition]:
 * - [NavigateTo] — navigate within or between sub-regions (use `AbsoluteTarget` to target a
 *   specific sub-region explicitly; a relative `FlowTarget`/`ScreenTarget` resolves against the
 *   first declared sub-region)
 * - [Finish] — finish the parallel-flow with a result (bubbles to parent flow's child-finish
 *   handler, or to [NavigationService.onFinishRequest] if root)
 * - [Stay] — consume the event with no navigation change
 * - [Ignore] — pass the event up to the parent node; for [Event.Back] this routes Back into the
 *   deepest active sub-region
 * - [EnqueueEvent] — schedule an event to be processed after the current transition completes
 * - [DispatchBackTo] — from `transition(Event.Back)` only: route the structural back-pop into the
 *   named sub-region (the app's own presentation field decides which one)
 * - [NavigateAndEnqueue] — navigate + chain follow-up events
 */
abstract class ParallelFlowNode<R : Any> : Node {
  abstract val dismissResult: R

  abstract fun transition(event: Event): FlowTransition<R>

  private var _eventSink: EventSink? = null

  /**
   * This parallel's sink ([NavigationService.eventSink]): events are resolved from the active leaves of its regions
   * and dropped with [DropReason.StaleSource] once this node instance has left navigation. A non-Back event nobody
   * handles in its scope falls back to the whole tree, like `send`. The service attaches a
   * new sink right before every entry (usable in `onEntry` and entry hooks), so if the node builder returns the same
   * instance on re-entry, reading this property later yields the sink of the new entry. For async work capture it
   * first (`val sink = eventSink` in `onEntry`, or before launching the work): the captured sink goes stale when this
   * entry ends. Events it sends from `onEntry` of a transition which is then rolled back are discarded without
   * [ServiceExtensionPoint.onEventDropped] (that entry never happened). Reading it before the first entry throws.
   */
  val eventSink: EventSink
    get() = checkNotNull(_eventSink) {
      "eventSink is not available before the runtime calls onEntry on this node"
    }

  internal fun attachEventSink(sink: EventSink) {
    _eventSink = sink
  }

  /**
   * Regions which start together with this node, as declared in its schema (`schema.<name>RegionId`). `null`
   * (default) starts all of them. A region which is not listed has no nodes until [startRegion], or a
   * [NavigateTo] with a target inside of it, starts it; then it lives as long as this node does. Must name at least one region of this node. Read on every entry.
   */
  open val initialRegions: Set<RegionId>? get() = null

  private var regionsPath: Path? = null
  private var regions: () -> Map<RegionId, Region> = { emptyMap() }
  private var startPayload: Any? = null

  /**
   * A transition which starts [regionId] from its initial node, or [Stay] if the region is already started.
   * Available from the first entry of this node.
   */
  fun startRegion(regionId: RegionId): FlowTransition<R> = if (isRegionStarted(regionId)) {
    Stay
  } else {
    val root = absoluteRegionId(regionId).path
    NavigateTo(AbsoluteTarget(root, payloads = startPayload?.let { mapOf(root to it) } ?: emptyMap()))
  }

  /** Whether [regionId] has nodes: it is one of [initialRegions] or has been started later. */
  fun isRegionStarted(regionId: RegionId): Boolean {
    val root = absoluteRegionId(regionId).path
    // a region whose root is a parallel flow is known only by the regions of that parallel
    return regions().keys.any { it.path.startsWith(root) }
  }

  /**
   * Whether [regionId] is started and rests on the node it has started with. Lets [transition] decide by the state
   * of a region, for example send Back to an overlay region only when it shows something.
   */
  fun isRegionAtRoot(regionId: RegionId): Boolean =
    regions()[absoluteRegionId(regionId)]?.let { it.active == it.rootPath } ?: false

  private fun absoluteRegionId(regionId: RegionId): RegionId {
    val path = checkNotNull(regionsPath) { "regions are not available before the runtime calls onEntry on this node" }
    return regionId.resolveAbsolute(path)
  }

  internal fun attachRegions(path: Path, startPayload: Any?, regions: () -> Map<RegionId, Region>) {
    regionsPath = path
    this.startPayload = startPayload
    this.regions = regions
  }
}

/** Whether the region with the root at [regionRoot] starts together with this node at [parallelPath]. */
internal fun ParallelFlowNode<*>.startsRegion(parallelPath: Path, regionRoot: Path): Boolean {
  val initial = initialRegions ?: return true
  return initial.any { it.resolveAbsolute(parallelPath).path == regionRoot }
}

/** Fails if [ParallelFlowNode.initialRegions] of this node at [parallelPath] names no region out of [regionRoots]. */
internal fun ParallelFlowNode<*>.checkInitialRegions(parallelPath: Path, regionRoots: List<Path>) {
  val initial = initialRegions?.map { it.resolveAbsolute(parallelPath).path } ?: return
  check(initial.isNotEmpty()) {
    "initialRegions of the parallel flow at \"$parallelPath\" must name at least one region"
  }
  check(regionRoots.containsAll(initial)) {
    "initialRegions of the parallel flow at \"$parallelPath\" must name its own regions $regionRoots, but were $initial"
  }
}
