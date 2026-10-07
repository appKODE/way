package ru.kode.way

import ru.kode.way.extension.node.hook.BaseFlowNode
import ru.kode.way.extension.node.hook.BaseScreenNode

/**
 * Drives navigation state for a single root flow.
 *
 * **Threading:** NavigationService is NOT thread-safe. All calls to [send], [start],
 * [addTransitionListener], etc. must be made from the same thread (typically the main/UI thread).
 * The reentrancy guard ([isDispatching]) only protects against single-threaded re-entry from
 * within listener callbacks, not concurrent access from multiple threads.
 */
class NavigationService<R : Any>(nodeBuilder: NodeBuilder, private val onFinishRequest: (R) -> FlowTransition<Unit>) :
  EventSink {
  private val nodeBuilder = TransitionNodeBuilder(nodeBuilder)
  private var state: NavigationState = NavigationState(
    _regions = mutableMapOf(),
    _nodeExtensionPoints = mutableListOf(),
    _enqueuedEvents = ArrayDeque(initialCapacity = 3),
  )
  private val listeners = ArrayList<(NavigationState) -> Unit>()
  private val serviceExtensionPoints = mutableListOf<ServiceExtensionPoint<R>>()
  private var enqueuedEventScheduler: ((Event) -> Unit)? = null

  /** Sink events handed unwrapped to [enqueuedEventScheduler], re-wrapped when the scheduler sends them back. */
  private val scheduledSourcedEvents = mutableListOf<SourcedEvent>()
  private var isDispatching = false
  private var isDisposed = false
  private var nextGeneration = 0L

  /**
   * [onFinishRequest] with its types erased to `(Any) -> Transition`. A root/intermediate parallel-flow
   * (whose `R` is bound to the service's `R`) routes its `Finish` result through this builder to reach
   * [onFinishRequest]. Computed once here rather than casting inline at each use site.
   */
  @Suppress("UNCHECKED_CAST")
  private val erasedFinishRequest: (Any) -> Transition = onFinishRequest as (Any) -> Transition

  /**
   * The finish-transition builder for a node rooted at [path]. A schema-root path (single segment)
   * means the node sits AT the service root, so its `Finish` bubbles to [onFinishRequest] via
   * [erasedFinishRequest]; otherwise `Finish` routes through [computeSubRegionFinishBuilder] so the
   * enclosing parallel-flow sees a typed `ChildFinishRequest`.
   */
  private fun finishBuilderFor(path: Path): (Any) -> Transition =
    if (path.isSchemaRoot) erasedFinishRequest else computeSubRegionFinishBuilder(nodeBuilder.schema, RegionId(path))

  /**
   * When true, validates that nodes returned by [NodeBuilder] match the types declared in the schema on every
   * transition. Enabled by default; can be disabled in production builds for performance.
   */
  var validateSchema: Boolean = true

  /**
   * When true, [send] throws [EventDroppedException] for an event dropped with [DropReason.MissingPayload].
   * When false (default), the event is dropped silently: navigation state stays as it was, no transition
   * listeners are called and the remaining enqueued events are still processed. In both cases every
   * [ServiceExtensionPoint.onEventDropped] is called first. Enable it in debug builds and tests.
   *
   * [DropReason.StaleSource] never throws, even in strict mode: an event from a node which has just left (a double
   * tap, a tap during an exit animation) is an expected race, it is only reported via
   * [ServiceExtensionPoint.onEventDropped].
   */
  var strictEventDropping: Boolean = false

  fun start(rootFlowPayload: Any? = null) {
    check(!isStarted()) { "NavigationService is already started; start() must only be called once" }
    dispatch(InitEvent(rootFlowPayload))
  }

  fun isStarted(): Boolean = state.isInitialized()

  /** A copy of the current state, `null` before the service is started. */
  fun currentState(): NavigationState? = if (state.isInitialized()) state.copy() else null

  /**
   * Registers a listener that receives the [NavigationState] after every transition.
   *
   * If [start] has already been called, the listener is invoked immediately with the current state
   * as part of registration. If that immediate-invocation throws, the listener is automatically
   * removed before the exception propagates to the caller.
   *
   * During normal dispatch (inside [send]), listeners are notified independently — one
   * listener throwing does NOT skip subsequent listeners. The first thrown exception propagates
   * out of [send] after every listener has been called; further exceptions thrown by later
   * listeners are suppressed and attached as `Throwable.suppressed` to the first one.
   */
  fun addTransitionListener(listener: (NavigationState) -> Unit) {
    if (isDisposed) return
    listeners.add(listener)
    if (state.isInitialized()) {
      try {
        listener(state.copy())
      } catch (e: Throwable) {
        listeners.remove(listener)
        throw e
      }
    }
  }

  fun removeTransitionListener(listener: (NavigationState) -> Unit) {
    if (isDisposed) return
    listeners.remove(listener)
  }

  fun addNodeExtensionPoint(point: NodeExtensionPoint) {
    if (isDisposed) return
    state._nodeExtensionPoints.add(point)
  }

  fun removeNodeExtensionPoint(point: NodeExtensionPoint) {
    if (isDisposed) return
    state._nodeExtensionPoints.remove(point)
  }

  fun addServiceExtensionPoint(point: ServiceExtensionPoint<R>) {
    if (isDisposed) return
    serviceExtensionPoints.add(point)
  }

  fun removeServiceExtensionPoint(point: ServiceExtensionPoint<R>) {
    if (isDisposed) return
    serviceExtensionPoints.remove(point)
  }

  /**
   * Releases all listeners and extension points held by this service.
   *
   * After calling [dispose], calls to [send] become safe no-ops: they will return immediately
   * without processing the event or delivering state to any listener. Calling [start] after [dispose]
   * is undefined behaviour and should be avoided.
   *
   * **Note:** [dispose] does NOT call [Node.onExit] or [Node.onDispose] on currently-alive nodes.
   * Use [cleanDispose] if you need [Node.onDispose] to fire on all alive nodes before shutdown.
   *
   * This method is idempotent: calling it more than once has no additional effect.
   */
  fun dispose() {
    if (isDisposed) return
    check(!isDispatching) {
      "dispose() must not be called during event dispatch (e.g. from inside a transition " +
        "listener or extension point). Call it after send() returns. Use cleanDispose() " +
        "for the same constraint with leaf-to-root onDispose firing."
    }
    isDisposed = true
    listeners.clear()
    scheduledSourcedEvents.clear()
    serviceExtensionPoints.clear()
    state._nodeExtensionPoints.clear()
    state._enqueuedEvents.clear()
    state._regions.clear()
    state._intermediateParallels.clear()
  }

  /**
   * Calls [Node.onDispose] on every currently-alive node in leaf-to-root order, then performs
   * the same hard stop as [dispose].
   *
   * Ordering guarantees:
   * - Sub-regions are disposed before the [ParallelFlowNode] that owns them.
   * - Within each region nodes are disposed leaf-first (deepest active node before its ancestors).
   * - The relative order between sibling sub-regions at the same depth is unspecified.
   *
   * Each step — extension-point `onPreDispose`, [Node.onDispose], and extension-point
   * `onPostDispose` — is individually wrapped in `runCatching`, so a throwing hook or node
   * does not skip the remaining steps or the remaining nodes.
   *
   * Must not be called from inside a transition listener or extension-point callback (i.e. during
   * event dispatch). Call it only after [send] returns, typically from [android.arch.lifecycle.ViewModel.onCleared].
   *
   * This method is idempotent: calling it more than once has no additional effect.
   */
  fun cleanDispose() {
    if (isDisposed) return
    check(!isDispatching) {
      "cleanDispose() must not be called during event dispatch (e.g. from inside a transition " +
        "listener or extension point). Call it after send() returns."
    }
    if (state.isInitialized()) {
      // a parallel entered by NavigateTo is both a node of the calling region and an intermediate parallel
      val disposed = mutableListOf<Node>()
      val disposeOnce = { node: Node, path: Path ->
        if (disposed.none { it === node }) {
          disposed.add(node)
          callOnDispose(node, path, state._nodeExtensionPoints)
        }
      }
      state._regions.entries
        .sortedByDescending { it.key.path.length }
        .forEach { (_, region) ->
          region.alive.reversed().forEach { path ->
            val node = region.nodes[path] ?: return@forEach
            disposeOnce(node, path)
          }
        }
      // Dispose intermediate parallel roots (parallel-rooted sub-region roots wrapping another
      // parallel-rooted schema) AFTER their inner regions — deepest-first so the outermost
      // intermediate parallel sees its own children disposed before it is.
      state._intermediateParallels.entries
        .sortedByDescending { it.key.length }
        .forEach { (path, intermediate) ->
          disposeOnce(intermediate.node, path)
        }
      // For a parallel-flow-ROOTED schema the root ParallelFlowNode lives in `state.rootNode`
      // (set at InitEvent), NOT in any region's `_nodes` map nor in `_intermediateParallels`
      // (mountIntermediateParallel skips `parallelPath == rootNodePath`). Dispose it LAST — after
      // every sub-region and intermediate — so it observes its children disposed first, matching
      // the leaf-to-root guarantee. Without this its onDispose() never fires and any scope/DI it
      // holds leaks on cleanDispose().
      val rootNode = state.rootNode
      val rootNodePath = state.rootNodePath
      if (rootNode != null && rootNodePath != null) {
        disposeOnce(rootNode, rootNodePath)
      }
    }
    dispose()
  }

  /**
   * All-or-nothing snapshot of the mutable navigation state taken before a transition runs, so a
   * throw anywhere in [transition] can [restore] the exact pre-transition state. The `_regions`
   * entries are deep-copied at capture; every other collection is a shallow copy of immutable
   * references. For an [InitEvent] all captured collections are empty, so [restore] cleanly resets
   * a failed `start()` to the pre-init state.
   */
  private class TransactionSnapshot(state: NavigationState) {
    private val regions: Map<RegionId, Region> = state._regions.mapValues { it.value.copy() }
    private val enqueuedEvents: List<Event> = state._enqueuedEvents.toList()
    private val payloads: Map<Path, Any> = state._payloads.toMap()
    private val intermediateParallels: Map<Path, IntermediateParallel> = state._intermediateParallels.toMap()
    private val history: Map<Path, HistoryRecord> = state._history.toMap()
    private val generations: Map<Path, Long> = state._generations.toMap()
    private val rootNode: Node? = state.rootNode
    private val rootNodePath: Path? = state.rootNodePath
    private val rootFinishTransitionBuilder: ((Any) -> Transition)? = state._rootFinishTransitionBuilder

    fun restoreEnqueuedEvents(state: NavigationState) {
      state._enqueuedEvents.clear()
      state._enqueuedEvents.addAll(enqueuedEvents)
    }

    fun restore(state: NavigationState) {
      state._regions.clear()
      state._regions.putAll(regions)
      restoreEnqueuedEvents(state)
      state._payloads.clear()
      state._payloads.putAll(payloads)
      state._intermediateParallels.clear()
      state._intermediateParallels.putAll(intermediateParallels)
      state._history.clear()
      state._history.putAll(history)
      state._generations.clear()
      state._generations.putAll(generations)
      state.rootNode = rootNode
      state.rootNodePath = rootNodePath
      state._rootFinishTransitionBuilder = rootFinishTransitionBuilder
    }
  }

  private fun transition(state: NavigationState, event: Event, source: Path?): NavigationState {
    check(event is InitEvent || state.isInitialized()) {
      "send() was called before start(); call NavigationService.start() first"
    }
    // a child NodeBuilder which a failed transition has created or dropped is not kept: it may have been created
    // with the argument of a target which was never reached
    val nodeBuilderCache = nodeBuilder.snapshotCache()
    return try {
      val navigationState = try {
        runTransition(state, event, source)
      } catch (e: Throwable) {
        nodeBuilder.restoreCache(nodeBuilderCache)
        throw e
      }
      // Called after the transition is fully committed. Exceptions here propagate to the caller
      // but do not roll back navigation state — the transition has already completed.
      serviceExtensionPoints.toList().forEach { it.onPostTransition(this, event, navigationState.copy()) }
      navigationState
    } finally {
      nodeBuilder.endTransition()
    }
  }

  private fun runTransition(state: NavigationState, event: Event, source: Path?): NavigationState {
    // Snapshot every mutable slot before any mutation so a throw anywhere below restores the exact
    // pre-transition state (all-or-nothing). Taken before InitEvent populates regions, so a failed
    // start() restores to empty and is retryable.
    val snapshot = TransactionSnapshot(state)
    // Resolved and payload-checked before onPreTransition, so an event dropped with a missing payload only reaches
    // onEventDropped. InitEvent is resolved after its regions are materialized, in applyResolvedTransition.
    val resolvedTransition = if (event is InitEvent) null else resolveCheckedTransition(state, event, source)
    serviceExtensionPoints.toList().forEach {
      it.onPreTransition(this, event, state.copy())
    }
    // Every node which got its onEntry or onExit during this transition, in the order of the calls.
    val entered = mutableListOf<Pair<Node, Path>>()
    val exited = mutableListOf<Pair<Node, Path>>()
    return try {
      applyResolvedTransition(state, event, source, resolvedTransition, entered, exited)
    } catch (e: Throwable) {
      // All-or-nothing: the state is restored first, so a node which is entered back sees the regions it had,
      // then every lifecycle call made so far is reversed.
      snapshot.restore(state)
      compensateLifecycle(entered, exited, event, state._nodeExtensionPoints)
      // an event which a node has sent while it was entered back belongs to the transition which has failed
      snapshot.restoreEnqueuedEvents(state)
      throw e
    }
  }

  /**
   * Runs the committed body of a [transition]: materializes regions on InitEvent, resolves the event
   * to target paths, recomputes the alive set, then synchronizes node lifecycles. Mutates [state] in
   * place and returns the same instance. Appends every node which got its `onEntry` to [entered] and
   * every node which got its `onExit` to [exited]: the caller ([transition]) owns the snapshot/restore and the
   * compensation of these calls for all-or-nothing rollback.
   */
  private fun applyResolvedTransition(
    state: NavigationState,
    event: Event,
    source: Path?,
    preResolvedTransition: ResolvedTransition?,
    entered: MutableList<Pair<Node, Path>>,
    exited: MutableList<Pair<Node, Path>>,
  ): NavigationState {
    if (event is InitEvent) {
      enterRootParallelIfNeeded(state, event, entered)
      val rootParallel = state.rootNode as? ParallelFlowNode<*>
      val rootParallelPath = state.rootNodePath
      // regions of a parallel declared deeper start when that parallel is entered
      val rootRegions = nodeBuilder.schema.regions.filter(nodeBuilder.schema::isRootRegion)
      if (rootParallel != null && rootParallelPath != null) {
        rootParallel.checkInitialRegions(rootParallelPath, rootRegions.map { it.path })
      }
      rootRegions.forEach { regionId ->
        val starts = rootParallel == null || rootParallelPath == null ||
          rootParallel.startsRegion(rootParallelPath, regionId.path)
        if (starts) materializeRegion(regionId, event, entered)
      }
    }
    val resolvedTransition = preResolvedTransition ?: resolveCheckedTransition(state, event, source)
    // An alive node re-targeted with a different argument is rebuilt, together with its alive descendants.
    val aliveBefore = computeConfiguration(state)
    val retargeted = resolvedTransition.payloads.filter { (path, payload) ->
      path in aliveBefore && path !in state._intermediateParallels &&
        state._payloads[path].let { it != null && it != payload }
    }.keys
    // Persist this transition's payloads into the running store BEFORE synchronizeNodes —
    // any lazily-rebuilt NodeBuilder with a parameterised flow lookup hits the store, not the
    // transient transition map. This is what fixes "no payload for <path>" on subsequent
    // re-builds (e.g. after `invalidateCache` removes a previously-cached child NodeBuilder
    // and a later event causes it to be rebuilt without its own NavigateTo).
    state._payloads.putAll(resolvedTransition.payloads)
    val previousAlive = state._regions.mapValues { it.value.alive.toList() }
    val previousNodes = state._regions.mapValues { it.value.nodes.toMap() }
    val premounted = premountIntermediates(state, event, resolvedTransition)
    val unmountedIntermediates = mutableSetOf<Path>()
    // calculateAliveNodes mutates and returns the SAME state instance; mutatedState === state.
    val mutatedState = calculateAliveNodes(state, resolvedTransition.targetPaths, nodeBuilder.schema)
    exitOrphanedParallels(state, event, previousAlive, previousNodes, exited, unmountedIntermediates, premounted)
    // An intermediate parallel below a re-targeted node is kept, only region nodes are rebuilt
    val recreated = computeConfiguration(mutatedState).filterTo(mutableSetOf()) { path ->
      path !in mutatedState._intermediateParallels && retargeted.any { path.startsWith(it) }
    }
    // A recreated node gets a new generation, so a sink of its previous instance is stale.
    mutatedState._generations.keys.removeAll(recreated)
    // Before synchronizeNodes, so a sink obtained in onEntry already carries the entered node's generation.
    syncGenerations(mutatedState)
    synchronizeNodes(
      mutatedState,
      event,
      mutatedState._payloads,
      previousAlive,
      entered,
      exited,
      unmountedIntermediates,
      recreated,
      premounted,
    )
    if (validateSchema) checkSchemaValidity(nodeBuilder.schema, mutatedState)
    mutatedState._enqueuedEvents.addAll(resolvedTransition.enqueuedEvents.orEmpty())
    return mutatedState
  }

  /**
   * Resolves [event] to its target paths without mutating [state], and throws [MissingPayloadException] when a
   * target needs a payload nobody has, before anything is mounted or exited.
   */
  private fun resolveCheckedTransition(state: NavigationState, event: Event, source: Path?): ResolvedTransition {
    // Shared by the scoped resolution and its fallback, so no node is asked about the event twice.
    val consulted = if (source != null && event != Event.Back) mutableSetOf<Path>() else null
    fun resolve(from: Path?) = resolveTransition(
      regions = state.regions,
      nodeBuilder = nodeBuilder,
      event = event,
      extensionPoints = state._nodeExtensionPoints,
      rootNode = state.rootNode,
      rootNodePath = state.rootNodePath,
      rootFinishTransitionBuilder = state._rootFinishTransitionBuilder,
      intermediateParallels = state._intermediateParallels,
      history = state._history,
      source = from,
      consulted = consulted,
    )
    var resolvedTransition = resolve(source)
    // An event nobody handled in the sink's scope goes on to the rest of the tree, in the order send would ask it
    // (Back never does). A parallel answering Stay resolves to EMPTY as well, so the event goes on past it too.
    if (source != null && event != Event.Back && resolvedTransition == ResolvedTransition.EMPTY) {
      resolvedTransition = resolve(null)
    }
    findMissingPayload(nodeBuilder.schema, state, resolvedTransition)?.let { throw MissingPayloadException(it) }
    return resolvedTransition
  }

  /** Gives a fresh generation to every newly alive path and forgets the paths which are no longer alive. */
  private fun syncGenerations(state: NavigationState) {
    val alive = computeConfiguration(state) + state._intermediateParallels.keys + listOfNotNull(state.rootNodePath)
    state._generations.keys.retainAll(alive)
    alive.forEach { path -> state._generations.getOrPut(path) { nextGeneration++ } }
  }

  /**
   * Returns a sink which sends events on behalf of the node currently alive at [path]. A screen's sink starts at the
   * screen itself (never at a screen stacked on it); a flow's or a parallel's sink starts at the active leaf of every
   * region under it, so its active children handle the event first, and the parallels under it handle it too. The
   * event then bubbles up on [Ignore] through the node to its ancestors within the region and, except for
   * [Event.Back], also reaches every parallel enclosing the node and bubbles up through each parallel's ancestors,
   * like [send] (same region order, same merge). If nothing in that scope handled the event, it falls back to the rest
   * of the tree, in the order [send] would ask it, so a sibling region can handle it; the nodes already asked are not
   * asked again. Back never falls back: it stays in the node's
   * region, never reaching enclosing parallels or sibling regions: routed through the node's own `DispatchBackTo` if it is a
   * parallel, kept in the node's region otherwise. The root node's sink is equivalent to [send].
   *
   * If the node is no longer alive, or has been recreated, when the event is dispatched, the event is dropped with
   * [DropReason.StaleSource]. A change below the node does not make its sink stale. A sink for a path which is not
   * alive is always stale.
   */
  fun eventSink(path: Path): EventSink = eventSink(path, state)

  private fun eventSink(path: Path, state: NavigationState): EventSink {
    val generation = state._generations[path] ?: -1L
    return EventSink { dispatch(SourcedEvent(it, path, generation)) }
  }

  /**
   * Enters [node] at [path]: a node owning a sink (see [BaseFlowNode.eventSink]) gets a fresh one bound to its
   * current generation first, so the sink is usable from `onEntry` and its hooks. Its generation must be assigned.
   *
   * Only forward entries go through here. The re-entry in [compensateLifecycle] after a rollback intentionally does
   * not re-attach: the node keeps the sink of its original entry, which is live again because [TransactionSnapshot]
   * restores `_generations`.
   *
   * The node is added to [entered] right before its `onEntry`: a node which is there gets `onExit` on a rollback.
   */
  private fun enter(
    state: NavigationState,
    node: Node,
    path: Path,
    event: Event,
    entered: MutableList<Pair<Node, Path>>,
  ) {
    when (node) {
      is BaseFlowNode<*> -> node.attachEventSink(eventSink(path, state))

      is BaseScreenNode -> node.attachEventSink(eventSink(path, state))

      is ParallelFlowNode<*> -> {
        node.attachEventSink(eventSink(path, state))
        // the root parallel gives the start payload to its regions which start later
        val startPayload = (event as? InitEvent)?.payload.takeIf { path.length == 1 }
        node.attachRegions(path, startPayload) { state._regions }
      }

      else -> Unit
    }
    callOnEntry(node, path, event, state._nodeExtensionPoints) { entered.add(node to path) }
  }

  /**
   * The generation of the node instance currently alive at [path], or `null` if no node is alive there. It changes
   * every time a node is (re)created at [path], so it can key a cached [eventSink].
   */
  fun nodeGeneration(path: Path): Long? = state._generations[path]

  /**
   * For a parallel-flow-ROOTED schema, builds and enters the root [ParallelFlowNode] FIRST (before
   * its sub-regions) so its `onEntry` fires and its `ComposableNode.Content` can render.
   * The parallel-flow lives at the schema's `rootSegment` path — one segment shorter than each
   * sub-region path — and is NOT iterated by `schema.regions`, so without this step it would never
   * be constructed. No-op for a flow-rooted schema: its root flow is the first region. Appends the entered root to
   * [initEnteredRoots] so the caller's catch can compensate its `onEntry` on a later failure.
   */
  private fun enterRootParallelIfNeeded(
    state: NavigationState,
    event: InitEvent,
    initEnteredRoots: MutableList<Pair<Node, Path>>,
  ) {
    val rootSegmentPath = Path(nodeBuilder.schema.rootSegment)
    val firstRegion = nodeBuilder.schema.regions.firstOrNull()
    val rootIsParallelFlow = firstRegion != null && firstRegion.path != rootSegmentPath &&
      firstRegion.path.startsWith(rootSegmentPath)
    if (!rootIsParallelFlow) return
    // InitEvent payload is consumed exactly once by this build; do NOT persist it in
    // `state._payloads` — the root flow node is built once and never rebuilt, and a length-1 root
    // path would crash the downstream `mapKeys { drop(...) }` chain on any later deep build.
    val rootNode = nodeBuilder.build(
      rootSegmentPath,
      payloads = event.payload?.let { mapOf(rootSegmentPath to it) } ?: emptyMap(),
      // For the parallel root the path equals rootPath so `targetOrError` isn't consulted, but
      // passing the alias is consistent with how the sub-region builds are invoked.
      rootSegmentAlias = nodeBuilder.schema.rootSegment,
    )
    require(rootNode is ParallelFlowNode<*>) {
      "schema rootSegment is a parallel-flow region root, but builder returned " +
        "${rootNode::class.simpleName}. Generated NodeBuilder is out of sync with the schema."
    }
    // Entered before syncGenerations runs: give it its generation now so a sink obtained in onEntry is live.
    state._generations.getOrPut(rootSegmentPath) { nextGeneration++ }
    enter(state, rootNode, rootSegmentPath, event, initEnteredRoots)
    state.rootNode = rootNode
    state.rootNodePath = rootSegmentPath
    state._rootFinishTransitionBuilder = erasedFinishRequest
  }

  /**
   * Pre-mount step (runtime NavigateTo only): BEFORE [calculateAliveNodes]' prune runs, mount any
   * intermediate parallel a target path passes through that isn't yet in `_intermediateParallels`.
   * Without this, [calculateAliveNodes] prunes the freshly-activated sub-region because its parent
   * intermediate isn't yet registered. The mounted parallels are built and registered only, their paths are
   * returned: [synchronizeNodes] enters them together with the nodes of the regions, after the nodes which are left
   * got their `onExit` and after the flows they are declared in got their `onEntry`.
   */
  private fun premountIntermediates(
    state: NavigationState,
    event: Event,
    resolvedTransition: ResolvedTransition,
  ): MutableSet<Path> {
    val premounted = mutableSetOf<Path>()
    if (event is InitEvent) return premounted
    // intermediateParallelAncestors returns shallowest-first; LinkedHashSet keeps that ordering
    // across multiple targets while de-duplicating.
    val premountOrdered = LinkedHashSet<Path>()
    resolvedTransition.targetPaths.values.forEach { targetPath ->
      premountOrdered.addAll(intermediateParallelAncestors(targetPath, nodeBuilder.schema))
    }
    premountOrdered.forEach { intermediatePath ->
      if (intermediatePath in state._intermediateParallels) return@forEach
      // a parallel which an initial target has entered is alive as a node of the calling region only
      val alive = state._regions.values.firstNotNullOfOrNull { it._nodes[intermediatePath] as? ParallelFlowNode<*> }
      if (alive != null) {
        state._intermediateParallels[intermediatePath] =
          IntermediateParallel(node = alive, finishBuilder = finishBuilderFor(intermediatePath), initMounted = false)
      } else {
        mountIntermediateParallel(intermediatePath, event, state._payloads, entered = null)
        // the root parallel is not an intermediate one, it is entered at start
        if (intermediatePath in state._intermediateParallels) premounted.add(intermediatePath)
      }
    }
    return premounted
  }

  /**
   * Leaves the parallels which the new configuration does not pass through any more: runtime-mounted intermediate
   * parallels (the ones mounted at InitEvent live as long as the service does) and the regions which lived only
   * because of them. Leaving a parallel can orphan a parallel declared inside of one of its regions, so the sweep
   * repeats until nothing is left to drop.
   *
   * The structure is settled first, `onExit` is called after that, deepest path first: the nodes of a region exit
   * before the parallel which owns the region, an inner parallel before the outer one. Every node gets its `onExit`
   * even if an earlier one throws; the throws are rethrown together.
   */
  private fun exitOrphanedParallels(
    state: NavigationState,
    event: Event,
    previousAlive: Map<RegionId, List<Path>>,
    previousNodes: Map<RegionId, Map<Path, Node>>,
    exited: MutableList<Pair<Node, Path>>,
    unmountedIntermediates: MutableSet<Path>,
    premounted: MutableSet<Path>,
  ) {
    if (event is InitEvent) return
    val leaving = mutableMapOf<Path, Node>()
    do {
      val configuration = computeConfiguration(state)
      val orphaned = state._intermediateParallels.filter { (path, intermediate) ->
        !intermediate.initMounted && path !in configuration
      }
      orphaned.forEach { (path, intermediate) ->
        state._intermediateParallels.remove(path)
        unmountedIntermediates.add(path)
        // a parallel which this transition has mounted is not entered yet
        if (!premounted.remove(path)) leaving[path] = intermediate.node
      }
      pruneOrphanRegions(state, nodeBuilder.schema)
    } while (orphaned.isNotEmpty())
    previousAlive.forEach { (regionId, paths) ->
      if (regionId !in state._regions) {
        // a region above a parallel carries the path of that parallel as well, the node is the same one
        paths.forEach { path -> previousNodes[regionId]?.get(path)?.let { leaving.getOrPut(path) { it } } }
      }
    }
    val onExitThrows = mutableListOf<Throwable>()
    leaving.entries.sortedByDescending { it.key.length }.forEach { (path, node) ->
      runCatching { callOnExit(node, path, event, state._nodeExtensionPoints) { exited.add(node to path) } }
        .onFailure { onExitThrows.add(it) }
    }
    onExitThrows.rethrowAsAggregate()
  }

  /**
   * Materialises one region root during InitEvent processing. If the builder returns a
   * [FlowNode], this is the historical behaviour: enter it, register the region. If the builder
   * returns a [ParallelFlowNode], the sub-region's referenced schema is itself parallel-rooted
   * (parallel-rooted nested inside parallel-rooted, see `parallel-test-nested-root.dot`); enter
   * the intermediate parallel, record it in [NavigationState._intermediateParallels] so
   * [cleanDispose] can fire `onDispose` on it later, then recurse into the inner schema's own
   * regions and materialise each of them at the right absolute path. The intermediate parallel
   * itself is NOT exposed as a runtime region.
   */
  private fun materializeRegion(
    regionId: RegionId,
    event: InitEvent,
    initEnteredRoots: MutableList<Pair<Node, Path>>,
  ) {
    val regionRootPath = regionId.path
    // Same reasoning as the parallel-rooted branch above: InitEvent payload is for the
    // region root and only consumed by this single build call. Do not persist.
    val regionRoot = nodeBuilder.build(
      regionRootPath,
      payloads = event.payload?.let { mapOf(regionRootPath to it) } ?: emptyMap(),
      // For parallel-flow-rooted schemas, the codegen's `Schema.target` defaults
      // `rootSegment` to the SUB-region root. Without an alias, the top-level NodeBuilder's
      // `targetOrError(subRegionSegment)` lookup returns a relative path that doesn't match
      // the absolute `regionRootPath` we pass here, and `startsWith` fails. Passing the
      // schema's own root segment as the alias makes `rootSegment` resolve to the parallel
      // parent, so the sibling-injection path table in the generated schema returns the
      // correct absolute path. For non-parallel-rooted schemas this alias change is a no-op
      // (alias == regionRoot already by default).
      rootSegmentAlias = nodeBuilder.schema.rootSegment,
    )
    when (regionRoot) {
      is FlowNode<*> -> {
        state._generations.getOrPut(regionRootPath) { nextGeneration++ }
        enter(state, regionRoot, regionRootPath, event, initEnteredRoots)
        val rootFinishBuilder = finishBuilderFor(regionRootPath)
        state._regions[regionId] = Region(
          _nodes = mutableMapOf(regionRootPath to regionRoot),
          _active = regionRootPath,
          _alive = mutableListOf(regionRootPath),
          _rootFinishTransitionBuilder = rootFinishBuilder,
        )
      }

      is ParallelFlowNode<*> -> {
        // Intermediate parallel: enter it so onEntry fires and Compose can render its `Content()`,
        // then descend into its inner schema's regions. The intermediate parallel itself is NOT
        // a runtime region — sub-region creation/dispose machinery is unaffected. The inner
        // schema is discovered via findParentSchema(... inclusive = true), so its `regions` list
        // is in the inner schema's namespace and absoluteRegionRoot resolves each to its
        // absolute path under regionRootPath.
        mountIntermediateParallel(
          parallelPath = regionRootPath,
          event = event,
          payloads = event.payload?.let { mapOf(regionRootPath to it) } ?: emptyMap(),
          entered = initEnteredRoots,
          preBuiltNode = regionRoot,
          initMounted = true,
        )
        val (innerSchema, innerSchemaPath) = findParentSchema(
          nodeBuilder.schema,
          regionRootPath,
          inclusive = true,
        )
        // only the regions of this parallel: the ones of a parallel declared deeper start when it is entered
        val innerRegionRoots = regionRootsOf(innerSchema, innerSchemaPath, regionRootPath)
        regionRoot.checkInitialRegions(regionRootPath, innerRegionRoots)
        innerRegionRoots.forEach { innerAbsPath ->
          if (regionRoot.startsRegion(regionRootPath, innerAbsPath)) {
            materializeRegion(RegionId(innerAbsPath), event, initEnteredRoots)
          }
        }
      }

      is ScreenNode -> error(
        "expected FlowNode or ParallelFlowNode at $regionId, but builder returned " +
          ScreenNode::class.simpleName,
      )
    }
  }

  /**
   * Mounts an intermediate parallel at [parallelPath] — builds the [ParallelFlowNode], fires
   * `onEntry`, records it in [NavigationState._intermediateParallels], and appends it to
   * [entered] so the caller's compensation sweep can reverse the mount on a downstream throw.
   *
   * Used both by [materializeRegion] (InitEvent path, passes the already-built [preBuiltNode])
   * and by [premountIntermediates] (runtime NavigateTo path that lands on a sub-region under a
   * not-yet-mounted intermediate; passes `preBuiltNode = null` so this helper builds the node itself, and
   * `entered = null` because such a parallel is entered later, after the flows above it).
   *
   * No-op if [parallelPath] is already in [NavigationState._intermediateParallels] OR equals
   * the service's [NavigationState.rootNodePath] (the root parallel is handled directly by the
   * InitEvent block).
   */
  private fun mountIntermediateParallel(
    parallelPath: Path,
    event: Event,
    payloads: Map<Path, Any>,
    entered: MutableList<Pair<Node, Path>>?,
    preBuiltNode: Node? = null,
    initMounted: Boolean = false,
  ) {
    if (parallelPath in state._intermediateParallels) return
    if (parallelPath == state.rootNodePath) return
    val nodeType = runCatching { findNodeType(nodeBuilder.schema, parallelPath) }.getOrNull()
    check(nodeType == Schema.NodeType.ParallelFlow) {
      "mountIntermediateParallel called for non-parallel path \"$parallelPath\" (nodeType=$nodeType)"
    }
    val node = preBuiltNode ?: nodeBuilder.build(
      parallelPath,
      payloads = payloads.onBuildPath(parallelPath),
      rootSegmentAlias = nodeBuilder.schema.rootSegment,
    )
    require(node is ParallelFlowNode<*>) {
      "expected ParallelFlowNode at $parallelPath, but builder returned ${node::class.simpleName}"
    }
    state._generations.getOrPut(parallelPath) { nextGeneration++ }
    // without [entered] the caller enters the node itself
    if (entered != null) {
      enter(state, node, parallelPath, event, entered)
    }
    val finishBuilder = finishBuilderFor(parallelPath)
    state._intermediateParallels[parallelPath] = IntermediateParallel(
      node = node,
      finishBuilder = finishBuilder,
      initMounted = initMounted,
    )
  }

  private fun checkSchemaValidity(schema: Schema, state: NavigationState) {
    state.regions.forEach { (_, region) ->
      region._nodes.forEach { (path, node) ->
        val nodeType = findNodeType(schema, path)
        when (node) {
          is FlowNode<*> -> {
            // For imported-schema nodes that the parent schema marks as Flow, the imported
            // schema's root may actually be ParallelFlow. Accept either.
            check(nodeType == Schema.NodeType.Flow || nodeType == Schema.NodeType.ParallelFlow) {
              "according to schema, \"$path\" should be a $nodeType, but it is a ${FlowNode::class.simpleName}"
            }
          }

          is ParallelFlowNode<*> -> {
            // Same flexibility for the reverse case — imported parallel-flow schemas surface as
            // Flow at the boundary in the parent schema's nodeType table.
            check(nodeType == Schema.NodeType.ParallelFlow || nodeType == Schema.NodeType.Flow) {
              "according to schema, \"$path\" should be a $nodeType, but it is a ${ParallelFlowNode::class.simpleName}"
            }
          }

          is ScreenNode -> {
            check(nodeType == Schema.NodeType.Screen) {
              "according to schema, \"$path\" should be a $nodeType, but it is a ${ScreenNode::class.simpleName}"
            }
          }
        }
      }
    }
  }

  private fun synchronizeNodes(
    state: NavigationState,
    event: Event,
    payloads: Map<Path, Any>,
    previousAlive: Map<RegionId, List<Path>>,
    entered: MutableList<Pair<Node, Path>>,
    exited: MutableList<Pair<Node, Path>>,
    unmountedIntermediates: Set<Path>,
    recreated: Set<Path>,
    premounted: Set<Path>,
  ) {
    // Record SCXML history for every compound flow/region that just left the alive set, keyed by
    // its path → the atomic leaf that was active under it. Done here — after calculateAliveNodes
    // recomputed each region's alive chain but before onExit/prune below — so the read of the
    // now-current alive set is accurate. Rolled back by the transaction snapshot on any later throw.
    recordHistoryOnExit(state, previousAlive)
    // Every onExit below runs even if an earlier one throws, so one failing node does not skip the others; the
    // throws are rethrown together. [entered] and [exited] are compensated by the caller after it restores the state.
    val onExitThrows = mutableListOf<Throwable>()
    // Per-region synchronization
    state._regions.forEach { (regionId, region) ->
      previousAlive[regionId].orEmpty().reversed().forEach { path ->
        if (!region.alive.contains(path) || path in recreated) {
          // Skip intermediate parallels: their onExit was already fired by the pre-unmount
          // step in transition(). They were carried in this region's previousAlive as a
          // path-coverage placeholder (placed by initParallelAndRouteAbsolute's
          // `resolved[callingRegionId] = parallelPath` on the way IN); the pre-unmount
          // sweep handled the actual lifecycle teardown — emitting onExit again here would
          // double-exit.
          if (path in unmountedIntermediates) {
            return@forEach
          }
          val node = region._nodes[path] ?: error("state doesn't contain node at \"$path\"")
          runCatching { callOnExit(node, path, event, state._nodeExtensionPoints) { exited.add(node to path) } }
            .onFailure { onExitThrows.add(it) }
        }
      }
      region._nodes.keys.retainAll(region.alive.toSet() - recreated)
    }
    onExitThrows.rethrowAsAggregate()
    // A cached child NodeBuilder of a recreated node was created with the previous argument.
    if (recreated.isNotEmpty()) nodeBuilder.invalidateCache(computeConfiguration(state) - recreated)
    // Entries: the parallels mounted by this transition and the nodes newly added to the regions by
    // calculateAliveNodes. A node is entered after every node above it, in whichever region that one lives.
    val pendingEntries = LinkedHashMap<Path, () -> Unit>()
    premounted.forEach { path ->
      val node = state._intermediateParallels.getValue(path).node
      pendingEntries[path] = {
        enter(state, node, path, event, entered)
      }
    }
    state._regions.forEach { (_, region) ->
      region.alive.forEach { path ->
        if (!region._nodes.containsKey(path)) {
          // An intermediate parallel lives in _intermediateParallels and belongs to no region, but a calling
          // region whose NavigateTo lands beyond it carries its path in the `alive` list (via
          // initParallelAndRouteAbsolute's `resolved[callingRegionId] = parallelPath`). The region gets the same
          // node instance, so that alive == nodes holds without building or entering it once more.
          val intermediate = state._intermediateParallels[path]
          if (intermediate != null) {
            region._nodes[path] = intermediate.node
            return@forEach
          }
          pendingEntries[path] = {
            val node = nodeBuilder.build(path, payloads.onBuildPath(path), nodeBuilder.schema.rootSegment)
            region._nodes[path] = node
            enter(state, node, path, event, entered)
          }
        }
      }
    }
    while (pendingEntries.isNotEmpty()) {
      val path = pendingEntries.keys.first()
      val above = pendingEntries.keys.filter { it != path && path.startsWith(it) }.sortedBy { it.length }
      (above + path).forEach { pendingEntries.remove(it)?.invoke() }
    }
    // Prune the persistent payload store: drop entries whose key is not a prefix of any
    // alive path across all regions. Mirrors `region._nodes.keys.retainAll(region.alive)`
    // and the lazy-NodeBuilder cache invalidation below. Without pruning the map would
    // grow unboundedly and keep references to payload instances after the owning flow
    // has been disposed.
    val aliveAcrossRegions = computeConfiguration(state)
    state._payloads.keys.retainAll { payloadKey ->
      aliveAcrossRegions.any { alivePath -> alivePath.startsWith(payloadKey) }
    }
    // Invalidate the lazy-NodeBuilder cache ONCE with the union of every region's alive
    // paths. A per-region call would evict children alive only in sibling parallel regions
    // (e.g. invalidating with the profile region's active path drops the cached explore
    // NodeBuilder, even though explore is still active in another region — and recreating
    // it on next access constructs a fresh DI subcomponent, losing every scope-singleton
    // state held inside).
    nodeBuilder.invalidateCache(aliveAcrossRegions)
  }

  /**
   * The root sink: sends [event] on behalf of the whole tree, from outside any node (Activity back, a deep link, a
   * push). It is resolved from the active leaf of every region, like the root node's [eventSink], and is never stale.
   * Code acting on behalf of a node uses that node's sink instead (UI: `LocalEventSink`, a node: its
   * [BaseFlowNode.eventSink] / [BaseScreenNode.eventSink], a presenter: the sink its screen passes to it).
   */
  override fun send(event: Event) = dispatch(event)

  /**
   * The single entry of every event: [send], node sinks ([SourcedEvent]) and [start]. Re-entrant calls are queued.
   */
  private fun dispatch(event: Event) {
    if (isDisposed) return
    val scheduledIndex = scheduledSourcedEvents.indexOfFirst { it.event === event }
    val sendingEvent = if (scheduledIndex >= 0) scheduledSourcedEvents.removeAt(scheduledIndex) else event
    if (isDispatching) {
      state._enqueuedEvents.addLast(sendingEvent)
      return
    }
    var currentEvent: Event = sendingEvent
    while (true) {
      isDispatching = true
      try {
        // transition() has already rolled the state back when it throws. A missing payload drops the event
        // and keeps draining the queue; a failed start() is never dropped.
        // The only place a SourcedEvent is unwrapped: everything downstream sees the user's event.
        val sourced = currentEvent as? SourcedEvent
        val event = sourced?.event ?: currentEvent
        val newState = if (sourced != null && state._generations[sourced.source] != sourced.generation) {
          dropEvent(event, DropReason.StaleSource(sourced.source), cause = null)
          null
        } else {
          try {
            transition(state, event, sourced?.source)
          } catch (e: MissingPayloadException) {
            if (event is InitEvent) throw e
            dropEvent(event, DropReason.MissingPayload(e.path), e)
            null
          }
        }
        if (newState != null) {
          val validityErrors = newState.runValidityChecks()
          if (validityErrors.isNotEmpty()) {
            error(validityErrors.joinToString("\n", prefix = "internal error. State is inconsistent:\n"))
          }
          state = newState
          // Per-listener try/catch: one listener throwing must NOT skip subsequent listeners.
          // Collect every throw and rethrow the first after all listeners have been called, attaching
          // the rest as `addSuppressed` so the caller still sees them. The rethrow deliberately aborts
          // the enqueued-events drain: a listener exception is a consumer bug and callers rely on it
          // surfacing immediately with the queue left intact (see "listener exception propagates
          // immediately; enqueued events are not drained").
          val listenerThrows = mutableListOf<Throwable>()
          listeners.toList().forEach { listener ->
            try {
              listener(state.copy())
            } catch (e: Throwable) {
              listenerThrows.add(e)
            }
          }
          listenerThrows.rethrowAsAggregate()
        }
      } finally {
        isDispatching = false
      }
      currentEvent = state._enqueuedEvents.removeFirstOrNull() ?: break
      enqueuedEventScheduler?.let { scheduler ->
        val sourced = currentEvent as? SourcedEvent
        if (sourced != null) scheduledSourcedEvents.add(sourced)
        scheduler(sourced?.event ?: currentEvent)
        break
      }
    }
  }

  private fun dropEvent(event: Event, reason: DropReason, cause: Throwable?) {
    serviceExtensionPoints.toList().forEach { it.onEventDropped(this, event, reason) }
    // A stale source is an expected race (a double tap, a tap during an exit animation), not a bug.
    if (strictEventDropping && reason is DropReason.MissingPayload) throw EventDroppedException(event, reason, cause)
  }

  /**
   * Sets a custom scheduler for enqueued events.
   *
   * By default, enqueued events are drained immediately in an iterative loop inside [send].
   * If you need dispatch to be tied to a platform event loop (e.g. `Handler.post` on Android),
   * set a custom scheduler here. It will be called with the next queued event after each transition;
   * the scheduler is responsible for delivering that event back to [send] at the right time.
   *
   * An event sent through an [EventSink] is passed to the scheduler as the plain event the node sent. The service
   * remembers that instance: when the scheduler passes the same instance (`===`) back to [send], it is
   * dispatched as a sink event again, i.e. resolved from its node and dropped with [DropReason.StaleSource] if that
   * node has left meanwhile. So deliver the very instance you received, not a copy. An event object which is also sent
   * directly meanwhile (e.g. [BackEvent]) may swap the treatment between the two deliveries.
   */
  fun setEnqueuedEventsScheduler(scheduler: (Event) -> Unit) {
    enqueuedEventScheduler = scheduler
  }
}

/**
 * Records SCXML history for compound flows/regions that just left the alive set. For each region,
 * [previousAlive] holds the pre-transition alive chain (a single linear root→leaf path within a
 * region), so its last entry is the previously-active atomic leaf. Every strict ancestor of that
 * leaf which is no longer alive (i.e. the flow was exited, not merely navigated within) gets the
 * leaf recorded under its path in [NavigationState._history]. Storing the leaf satisfies both
 * shallow restore (derive the ancestor's immediate child from the leaf) and deep restore (the leaf
 * itself). A flow whose descendants are only navigated (the flow stays alive) is left untouched, so
 * its history is not overwritten until it is actually exited.
 *
 * Parallel-region deep history is handled by keying off the GLOBAL new configuration rather than a
 * single region's alive set: each exiting leaf walks its FULL absolute ancestry, so a child parallel
 * region's atomic leaf is associated with every exiting grandparent flow above the region root, and
 * the leaves of all sibling regions under one flow are unioned instead of overwriting each other.
 */
private fun recordHistoryOnExit(state: NavigationState, previousAlive: Map<RegionId, List<Path>>) {
  // The SCXML configuration that is alive AFTER this transition (union of every region's alive
  // chain). An ancestor left the alive set iff it is absent here — true across ALL regions, so a
  // grandparent flow above a parallel region root is detected even though that region's own chain
  // begins at the region root.
  val newConfig = computeConfiguration(state)
  val recorded = mutableMapOf<Path, MutableList<Path>>()
  previousAlive.forEach { (_, prevPaths) ->
    if (prevPaths.isEmpty()) return@forEach
    val prevLeaf = prevPaths.last()
    // Walk the FULL absolute ancestry of the previous leaf (parent → schema root). Every proper
    // ancestor that is no longer alive records this leaf; siblings accumulate rather than overwrite.
    getProperAncestors(prevLeaf, boundary = null).forEach { ancestor ->
      if (ancestor !in newConfig) {
        recorded.getOrPut(ancestor) { mutableListOf() }.add(prevLeaf)
      }
    }
  }
  recorded.forEach { (ancestor, leaves) ->
    // every not alive node on the way to a leaf, including parameterized ancestors above [ancestor]
    val payloads = state._payloads.filterKeys { path -> path !in newConfig && leaves.any { it.startsWith(path) } }
    state._history[ancestor] = HistoryRecord(leaves.distinct(), payloads)
  }
}

private fun NavigationState.runValidityChecks(): List<String> = regions.mapNotNull { (regionId, region) ->
  if (region.alive.toSet() != region.nodes.keys) {
    "region \"$regionId\": alive node path set is different from nodes set. Alive paths: " +
      "${region.alive}, alive nodes: ${region.nodes.keys}"
  } else {
    null
  }
}

private fun NavigationState.isInitialized(): Boolean = this.regions.isNotEmpty()

/**
 * Reverses a partially-applied set of lifecycle calls after a mid-transition throw: fires `onExit`
 * (reversed) for nodes that received `onEntry`, then `onEntry` (reversed) for nodes that received
 * `onExit`. Each call is `runCatching`'d so one throwing hook doesn't abort the rest. Pairs with the
 * transaction snapshot restore to keep entry/exit balanced when a dispatch rolls back.
 */
private fun compensateLifecycle(
  entered: List<Pair<Node, Path>>,
  exited: List<Pair<Node, Path>>,
  event: Event,
  extensionPoints: List<NodeExtensionPoint>,
) {
  entered.reversed().forEach { (node, path) ->
    runCatching { callOnExit(node, path, event, extensionPoints) }
  }
  exited.reversed().forEach { (node, path) ->
    // Not NavigationService.enter: no new sink is attached. The node's original sink becomes live again once the
    // caller's TransactionSnapshot restores `_generations`.
    runCatching { callOnEntry(node, path, event, extensionPoints) }
  }
}

/**
 * If non-empty, throws the first element with every subsequent element attached via
 * [Throwable.addSuppressed]; no-op when empty. Lets a loop run every consumer/listener and then
 * surface the first failure without losing the rest.
 */
private fun List<Throwable>.rethrowAsAggregate() {
  val primary = firstOrNull() ?: return
  drop(1).forEach { primary.addSuppressed(it) }
  throw primary
}

/**
 * Payloads on the build path of [path]: its ancestors (a parameterized intermediate flow the lazy
 * factory cascade looks up as it descends) and its descendants (reached by deeper builds). Unrelated
 * sibling keys are dropped so they cannot become an empty Path mid-`mapKeys { drop(N) }` cascade in
 * the generated NodeBuilder.
 */
private fun Map<Path, Any>.onBuildPath(path: Path): Map<Path, Any> =
  filterKeys { it.startsWith(path) || path.startsWith(it) }

private fun callOnEntry(
  node: Node,
  path: Path,
  event: Event,
  extensionPoints: List<NodeExtensionPoint>,
  onEntering: () -> Unit = {},
) {
  val snapshot = extensionPoints.toList()
  snapshot.forEach { it.onPreEntry(node, path) }
  onEntering()
  // Pass [path] to the new overload; default impl in [Node] delegates to the legacy
  // (event-only) variant for backward compatibility.
  node.onEntry(event, path)
  snapshot.forEach { it.onPostEntry(node, path) }
}

private fun callOnExit(
  node: Node,
  path: Path,
  event: Event,
  extensionPoints: List<NodeExtensionPoint>,
  onExiting: () -> Unit = {},
) {
  val snapshot = extensionPoints.toList()
  snapshot.forEach { it.onPreExit(node, path) }
  onExiting()
  node.onExit(event, path)
  snapshot.forEach { it.onPostExit(node, path) }
}

private fun callOnDispose(node: Node, path: Path, extensionPoints: List<NodeExtensionPoint>) {
  val snapshot = extensionPoints.toList()
  snapshot.forEach { runCatching { it.onPreDispose(node, path) } }
  runCatching { node.onDispose() }
  snapshot.forEach { runCatching { it.onPostDispose(node, path) } }
}

/**
 * Resolving a transition builds the nodes it has to ask (the initial target of a flow, the initial regions of
 * a parallel) before they are entered. Keeps such a node until the end of the transition, so the node which
 * is entered is the one which was asked.
 */
private class TransitionNodeBuilder(private val delegate: NodeBuilder) : NodeBuilder {
  private val built = mutableMapOf<Key, Node>()

  /** [payloads] are the ones a build of [path] reads: its own one and those of its ancestors. */
  private data class Key(val path: Path, val payloads: Map<Path, Any>, val rootSegmentAlias: Segment?)

  override val schema: Schema get() = delegate.schema

  override fun build(path: Path, payloads: Map<Path, Any>, rootSegmentAlias: Segment?): Node =
    built.getOrPut(Key(path, payloads.filterKeys(path::startsWith), rootSegmentAlias)) {
      delegate.build(path, payloads, rootSegmentAlias)
    }

  override fun invalidateCache(alivePaths: Set<Path>) {
    // a child of a recreated node was built by the child NodeBuilder which is dropped here
    built.keys.retainAll { it.path in alivePaths }
    delegate.invalidateCache(alivePaths)
  }

  override fun snapshotCache(): Any? = delegate.snapshotCache()

  override fun restoreCache(snapshot: Any?) = delegate.restoreCache(snapshot)

  fun endTransition() {
    built.clear()
  }
}
