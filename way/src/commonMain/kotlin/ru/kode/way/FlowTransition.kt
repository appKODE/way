package ru.kode.way

sealed interface Transition

sealed interface FlowTransition<out R : Any> : Transition
sealed interface ScreenTransition : Transition

/**
 * Navigates to one or more [Target] nodes.
 *
 * When returned from a [ScreenNode.transition], the targets must be siblings within the same
 * parent flow schema — i.e. nodes declared at the same level in the flow graph as the current
 * screen. Attempting to navigate to a node outside the current schema scope will result in a
 * resolution error at runtime.
 *
 * When returned from a [FlowNode.transition] or [ParallelFlowNode.transition], targets may refer to
 * any node reachable from the current schema, including sibling flows and parallel regions.
 *
 * ### Multiple targets
 *
 * The primary use of multiple targets is to set the active path in several parallel regions at
 * once (e.g. deep-linking into two sub-regions of a parallel flow simultaneously). [targets] is a
 * [List] — order is significant — with the following contract:
 * - Targets are applied **in list order**.
 * - Two targets that resolve to the **same region** collapse to the **last one** (its path and
 *   payload win). A duplicate/identical target is therefore idempotent.
 * - Targets that resolve to **different regions** are independent — the resulting state does not
 *   depend on their relative order.
 * - When multiple targets initialize the **same cold parallel**, the first target to reach it
 *   materializes its sub-regions and later targets refine specific ones without resetting the
 *   siblings the earlier target already placed.
 *
 * A [List] (not a [Set]) is used precisely so this ordering is explicit and a caller cannot pass
 * an unordered collection that would make same-region resolution non-deterministic.
 */
data class NavigateTo(val targets: List<Target>) :
  FlowTransition<Nothing>,
  ScreenTransition {

  init {
    require(targets.isNotEmpty()) {
      "NavigateTo requires at least one target. Use Stay to keep the current navigation, " +
        "or Ignore to defer to the parent."
    }
  }

  constructor(
    target: Target,
  ) : this(listOf(target))
}

/**
 * Finishes the current [FlowNode] with [result].
 *
 * The runtime delivers a finish-request event to the parent [FlowNode], which must handle it
 * with a matching event type. The [result] type [R] must match the result type declared in the
 * schema's child-finish event generated for this flow.
 *
 * Returning [Finish] from a root flow (one whose parent is the [NavigationService] itself)
 * invokes the [NavigationService]'s `onFinishRequest` callback.
 */
data class Finish<R : Any>(val result: R) : FlowTransition<R>

/**
 * Schedules [event] to be processed after the current transition (and all its side-effects)
 * have fully completed.
 *
 * **Ordering guarantee:** enqueued events are appended to a FIFO queue. If a transition produces
 * multiple [EnqueueEvent] results (across parallel regions), they are appended in region-iteration
 * order and each is dispatched one at a time. Each dispatched event may itself produce further
 * [EnqueueEvent] results, which are appended to the tail of the same queue.
 */
data class EnqueueEvent(val event: Event) :
  FlowTransition<Nothing>,
  ScreenTransition

/**
 * Composes a [NavigateTo] with one or more follow-up events that the runtime enqueues after the
 * navigation completes. Use the [thenEnqueue] infix function to construct it fluently:
 *
 * ```kotlin
 * return NavigateTo(Target.appFlow.homeFlow) thenEnqueue Sim.Details(simId)
 * ```
 *
 * The follow-up events join the same FIFO queue [EnqueueEvent] uses — they are dispatched after
 * the current transition's side-effects (including the navigation's onEntry hooks) complete.
 *
 * This removes the need for ad-hoc "post-entry action" plumbing — a previously common pattern was
 * to assisted-inject a lambda into the destination flow's root node and invoke it from `onEntry`.
 * With [NavigateAndEnqueue], the caller declares the follow-up at the navigation site, and the
 * destination flow handles the event through its normal `transition` function.
 */
data class NavigateAndEnqueue(val navigate: NavigateTo, val events: List<Event>) :
  FlowTransition<Nothing>,
  ScreenTransition {
  init {
    require(events.isNotEmpty()) {
      "NavigateAndEnqueue must carry at least one follow-up event; use plain NavigateTo otherwise."
    }
  }
}

infix fun NavigateTo.thenEnqueue(event: Event): NavigateAndEnqueue = NavigateAndEnqueue(this, listOf(event))

infix fun NavigateAndEnqueue.thenEnqueue(event: Event): NavigateAndEnqueue = copy(events = events + event)

/**
 * Routes a structural Back into the sub-region named by [regionId] when returned from a
 * [ParallelFlowNode.transition] handling [Event.Back].
 *
 * A parallel node coexists in several sub-regions at once, so a Back press has no inherent target.
 * Return `DispatchBackTo(regionId)` from `transition(Event.Back)` to declare which sub-region should
 * receive the Back — typically the one the app is currently presenting. The runtime then performs
 * the ordinary structural back-pop (screen pop / flow finish / nested-parallel recursion) inside
 * that region, exactly as if the Back had originated there.
 *
 * [regionId] may be either an absolute key from `NavigationState.regions` or a schema-local id
 * (such as `MyParallelFlowSchema.exploreFlowRegionId`); the runtime suffix-matches schema-local ids
 * against the parallel's active sub-regions. A stale or unresolved id never crashes Back — it
 * soft-falls-back to the deepest active sub-region.
 *
 * This is a parallel-only transition (declared `FlowTransition<Nothing>`). Returning it from a plain
 * [FlowNode] or [ScreenNode] is a misuse and is rejected at resolution time.
 */
data class DispatchBackTo(val regionId: RegionId) : FlowTransition<Nothing>

/**
 * Consumes the event and stays on the current node without changing navigation state.
 *
 * The navigation state is re-emitted to all listeners with the same active path, allowing
 * observers that subscribe to state updates to react even though no navigation occurred.
 */
object Stay : FlowTransition<Nothing>, ScreenTransition

/**
 * Ignores the event and bubbles it to the parent node for handling.
 *
 * If a [ScreenNode] returns [Ignore], the event is passed to the parent [FlowNode] or
 * [ParallelFlowNode]. If the root flow node also returns [Ignore] and there are no more
 * ancestors, the event is silently dropped — with one exception: for [Event.Back] received at a
 * region root with [Ignore], the runtime applies default back semantics (finishing the parent
 * flow with `dismissResult`, popping to the previous screen, or, at a parallel node, routing Back
 * into the deepest active sub-region) rather than dropping the event. A parallel node can override
 * that default by returning [DispatchBackTo] from its `transition(Event.Back)`.
 */
object Ignore : FlowTransition<Nothing>, ScreenTransition
