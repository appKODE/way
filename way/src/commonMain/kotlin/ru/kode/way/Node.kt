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
}
