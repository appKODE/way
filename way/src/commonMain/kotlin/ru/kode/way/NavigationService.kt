package ru.kode.way

/**
 * Drives navigation state for a single root flow.
 *
 * **Threading:** NavigationService is NOT thread-safe. All calls to [sendEvent], [start],
 * [addTransitionListener], etc. must be made from the same thread (typically the main/UI thread).
 * The reentrancy guard ([isDispatching]) only protects against single-threaded re-entry from
 * within listener callbacks, not concurrent access from multiple threads.
 */
class NavigationService<R : Any>(
  private val nodeBuilder: NodeBuilder,
  private val onFinishRequest: (R) -> FlowTransition<Unit>,
) {
  private var state: NavigationState = NavigationState(
    _regions = mutableMapOf(),
    _nodeExtensionPoints = mutableListOf(),
    _enqueuedEvents = ArrayDeque(initialCapacity = 3),
  )
  private val listeners = ArrayList<(NavigationState) -> Unit>()
  private val serviceExtensionPoints = mutableListOf<ServiceExtensionPoint<R>>()
  private var enqueuedEventScheduler: ((Event) -> Unit)? = null
  private var isDispatching = false
  private var isDisposed = false

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

  fun start(rootFlowPayload: Any? = null) {
    check(!isStarted()) { "NavigationService is already started; start() must only be called once" }
    sendEvent(InitEvent(rootFlowPayload))
  }

  fun isStarted(): Boolean = state.isInitialized()

  /**
   * Registers a listener that receives the [NavigationState] after every transition.
   *
   * If [start] has already been called, the listener is invoked immediately with the current state
   * as part of registration. If that immediate-invocation throws, the listener is automatically
   * removed before the exception propagates to the caller.
   *
   * During normal dispatch (inside [sendEvent]), listeners are notified independently — one
   * listener throwing does NOT skip subsequent listeners. The first thrown exception propagates
   * out of [sendEvent] after every listener has been called; further exceptions thrown by later
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
   * After calling [dispose], calls to [sendEvent] become safe no-ops: they will return immediately
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
        "listener or extension point). Call it after sendEvent() returns. Use cleanDispose() " +
        "for the same constraint with leaf-to-root onDispose firing."
    }
    isDisposed = true
    listeners.clear()
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
   * event dispatch). Call it only after [sendEvent] returns, typically from [android.arch.lifecycle.ViewModel.onCleared].
   *
   * This method is idempotent: calling it more than once has no additional effect.
   */
  fun cleanDispose() {
    if (isDisposed) return
    check(!isDispatching) {
      "cleanDispose() must not be called during event dispatch (e.g. from inside a transition " +
        "listener or extension point). Call it after sendEvent() returns."
    }
    if (state.isInitialized()) {
      state._regions.entries
        .sortedByDescending { it.key.path.length }
        .forEach { (_, region) ->
          region.alive.reversed().forEach { path ->
            val node = region.nodes[path] ?: return@forEach
            callOnDispose(node, path, state._nodeExtensionPoints)
          }
        }
      // Dispose intermediate parallel roots (parallel-rooted sub-region roots wrapping another
      // parallel-rooted schema) AFTER their inner regions — deepest-first so the outermost
      // intermediate parallel sees its own children disposed before it is.
      state._intermediateParallels.entries
        .sortedByDescending { it.key.length }
        .forEach { (path, intermediate) ->
          callOnDispose(intermediate.node, path, state._nodeExtensionPoints)
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
        callOnDispose(rootNode, rootNodePath, state._nodeExtensionPoints)
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
    private val history: Map<Path, List<Path>> = state._history.toMap()
    private val rootNode: Node? = state.rootNode
    private val rootNodePath: Path? = state.rootNodePath
    private val rootFinishTransitionBuilder: ((Any) -> Transition)? = state._rootFinishTransitionBuilder

    fun restore(state: NavigationState) {
      state._regions.clear()
      state._regions.putAll(regions)
      state._enqueuedEvents.clear()
      state._enqueuedEvents.addAll(enqueuedEvents)
      state._payloads.clear()
      state._payloads.putAll(payloads)
      state._intermediateParallels.clear()
      state._intermediateParallels.putAll(intermediateParallels)
      state._history.clear()
      state._history.putAll(history)
      state.rootNode = rootNode
      state.rootNodePath = rootNodePath
      state._rootFinishTransitionBuilder = rootFinishTransitionBuilder
    }
  }

  private fun transition(state: NavigationState, event: Event): NavigationState {
    check(event is InitEvent || state.isInitialized()) {
      "sendEvent() was called before start(); call NavigationService.start() first"
    }
    serviceExtensionPoints.toList().forEach {
      it.onPreTransition(this, event, state.copy())
    }
    // Snapshot every mutable slot before any mutation so a throw anywhere below restores the exact
    // pre-transition state (all-or-nothing). Taken before InitEvent populates regions, so a failed
    // start() restores to empty and is retryable.
    val snapshot = TransactionSnapshot(state)
    // Root flow nodes entered by the InitEvent block, tracked so the catch can compensate their
    // onEntry. applyResolvedTransition tracks its own entered/exited separately.
    val initEnteredRoots = mutableListOf<Pair<Node, Path>>()
    val navigationState = try {
      applyResolvedTransition(state, event, initEnteredRoots)
    } catch (e: Throwable) {
      // Compensate onEntry for root flow nodes entered in the InitEvent block (applyResolvedTransition
      // compensates its own nodes); then restore the snapshot so the whole dispatch is all-or-nothing.
      compensateLifecycle(initEnteredRoots, exited = emptyList(), event, state._nodeExtensionPoints)
      snapshot.restore(state)
      throw e
    }
    // Called after the transition is fully committed. Exceptions here propagate to the caller
    // but do not roll back navigation state — the transition has already completed.
    serviceExtensionPoints.toList().forEach { it.onPostTransition(this, event, navigationState.copy()) }
    return navigationState
  }

  /**
   * Runs the committed body of a [transition]: materializes regions on InitEvent, resolves the event
   * to target paths, recomputes the alive set, then synchronizes node lifecycles. Mutates [state] in
   * place and returns the same instance. Appends InitEvent-entered roots to [initEnteredRoots] so the
   * caller's catch can compensate them; its own inner lifecycle calls are compensated here on a throw.
   * The caller ([transition]) owns the snapshot/restore for all-or-nothing rollback.
   */
  private fun applyResolvedTransition(
    state: NavigationState,
    event: Event,
    initEnteredRoots: MutableList<Pair<Node, Path>>,
  ): NavigationState {
    if (event is InitEvent) {
      enterRootParallelIfNeeded(state, event, initEnteredRoots)
      nodeBuilder.schema.regions.forEach { regionId ->
        materializeRegion(regionId, event, initEnteredRoots)
      }
    }
    val resolvedTransition = resolveTransition(
      regions = state.regions,
      nodeBuilder = nodeBuilder,
      event = event,
      extensionPoints = state._nodeExtensionPoints,
      rootNode = state.rootNode,
      rootNodePath = state.rootNodePath,
      rootFinishTransitionBuilder = state._rootFinishTransitionBuilder,
      intermediateParallels = state._intermediateParallels,
      history = state._history,
    )
    // Persist this transition's payloads into the running store BEFORE synchronizeNodes —
    // any lazily-rebuilt NodeBuilder with a parameterised flow lookup hits the store, not the
    // transient transition map. This is what fixes "no payload for <path>" on subsequent
    // re-builds (e.g. after `invalidateCache` removes a previously-cached child NodeBuilder
    // and a later event causes it to be rebuilt without its own NavigateTo).
    state._payloads.putAll(resolvedTransition.payloads)
    val previousAlive = state._regions.mapValues { it.value.alive.toList() }
    val previousNodes = state._regions.mapValues { it.value.nodes.toMap() }
    // Hoisted out of synchronizeNodes so the runtime pre-mount and pre-unmount of intermediate
    // parallels can append to the same compensation lists — the inner catch below replays
    // every phase's enter/exit in lockstep regardless of which one threw.
    val syncEntered = mutableListOf<Pair<Node, Path>>()
    val syncExited = mutableListOf<Pair<Node, Path>>()
    premountIntermediates(state, event, resolvedTransition, syncEntered)
    val unmountedIntermediates = mutableSetOf<Path>()
    // calculateAliveNodes mutates and returns the SAME state instance; mutatedState === state.
    val mutatedState = calculateAliveNodes(state, resolvedTransition.targetPaths, nodeBuilder.schema)
    unmountOrphanedIntermediates(state, event, syncExited, unmountedIntermediates)
    synchronizeNodes(
      mutatedState,
      event,
      mutatedState._payloads,
      previousAlive,
      previousNodes,
      syncEntered,
      syncExited,
      unmountedIntermediates,
    )
    try {
      if (validateSchema) checkSchemaValidity(nodeBuilder.schema, mutatedState)
      mutatedState._enqueuedEvents.addAll(resolvedTransition.enqueuedEvents.orEmpty())
    } catch (e: Throwable) {
      compensateLifecycle(syncEntered, syncExited, event, mutatedState._nodeExtensionPoints)
      throw e
    }
    return mutatedState
  }

  /**
   * For a parallel-flow-ROOTED schema, builds and enters the root [ParallelFlowNode] FIRST (before
   * its sub-regions) so its `onEntry` fires and its `ComposableNode.Content` can render.
   * The parallel-flow lives at the schema's `rootSegment` path — one segment shorter than each
   * sub-region path — and is NOT iterated by `schema.regions`, so without this step it would never
   * be constructed. No-op for the common flow-rooted schema. Appends the entered root to
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
    callOnEntry(rootNode, rootSegmentPath, event, state._nodeExtensionPoints)
    initEnteredRoots.add(rootNode to rootSegmentPath)
    state.rootNode = rootNode
    state.rootNodePath = rootSegmentPath
    state._rootFinishTransitionBuilder = erasedFinishRequest
  }

  /**
   * Pre-mount step (runtime NavigateTo only): BEFORE [calculateAliveNodes]' prune runs, mount any
   * intermediate parallel a target path passes through that isn't yet in `_intermediateParallels`.
   * Without this, [calculateAliveNodes] prunes the freshly-activated sub-region because its parent
   * intermediate isn't yet registered. Shallowest-first so `onEntry` fires parent-before-child;
   * mounts are appended to [syncEntered] for compensation on a downstream throw.
   */
  private fun premountIntermediates(
    state: NavigationState,
    event: Event,
    resolvedTransition: ResolvedTransition,
    syncEntered: MutableList<Pair<Node, Path>>,
  ) {
    if (event is InitEvent) return
    // intermediateParallelAncestors returns shallowest-first; LinkedHashSet keeps that ordering
    // across multiple targets while de-duplicating.
    val premountOrdered = LinkedHashSet<Path>()
    resolvedTransition.targetPaths.values.forEach { targetPath ->
      premountOrdered.addAll(intermediateParallelAncestors(targetPath, nodeBuilder.schema))
    }
    premountOrdered.forEach { intermediatePath ->
      if (intermediatePath !in state._intermediateParallels) {
        mountIntermediateParallel(intermediatePath, event, state._payloads, syncEntered)
      }
    }
  }

  /**
   * Post-update unmount step (runtime NavigateTo only): now that each region's `alive` reflects this
   * transition, a class-1 (runtime-mounted) intermediate is orphaned iff its path no longer appears
   * in any region's `alive` (the path was placed there by `initParallelAndRouteAbsolute` on the way
   * IN, and removed by [calculateAliveNodes] when the new target doesn't pass through it). Class-2
   * (initMounted) intermediates are pinned for the service's lifetime and torn down only via
   * [cleanDispose]. Unmounts deepest-first (matching the [cleanDispose] contract: an outer
   * intermediate exits only AFTER its inner ones), recording exits in [syncExited] /
   * [unmountedIntermediates]. If anything was unmounted, re-runs [pruneOrphanRegions] so sub-regions
   * kept only by the "parent in _intermediateParallels" escape hatch are removed before
   * [synchronizeNodes] runs.
   */
  private fun unmountOrphanedIntermediates(
    state: NavigationState,
    event: Event,
    syncExited: MutableList<Pair<Node, Path>>,
    unmountedIntermediates: MutableSet<Path>,
  ) {
    if (event is InitEvent) return
    val configuration = computeConfiguration(state)
    val toUnmount = state._intermediateParallels.entries
      .asSequence()
      .filter { (_, intermediate) -> !intermediate.initMounted }
      .map { it.key }
      .filter { path -> path !in configuration }
      .toList()
    toUnmount.sortedByDescending { it.length }.forEach { intermediatePath ->
      val intermediate = state._intermediateParallels.remove(intermediatePath)!!
      callOnExit(intermediate.node, intermediatePath, event, state._nodeExtensionPoints)
      syncExited.add(intermediate.node to intermediatePath)
      unmountedIntermediates.add(intermediatePath)
    }
    if (toUnmount.isNotEmpty()) {
      pruneOrphanRegions(state, nodeBuilder.schema)
    }
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
        callOnEntry(regionRoot, regionRootPath, event, state._nodeExtensionPoints)
        initEnteredRoots.add(regionRoot to regionRootPath)
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
        innerSchema.regions.forEach { innerRelRegion ->
          val innerAbsPath = absoluteRegionRoot(innerSchemaPath, innerRelRegion)
          val innerAbsRegionId = RegionId(innerAbsPath)
          materializeRegion(innerAbsRegionId, event, initEnteredRoots)
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
   * and by the pre-mount step in [transition] (runtime NavigateTo path that lands on a sub-region
   * under a not-yet-mounted intermediate; passes `preBuiltNode = null` so this helper builds the
   * node itself).
   *
   * No-op if [parallelPath] is already in [NavigationState._intermediateParallels] OR equals
   * the service's [NavigationState.rootNodePath] (the root parallel is handled directly by the
   * InitEvent block).
   */
  private fun mountIntermediateParallel(
    parallelPath: Path,
    event: Event,
    payloads: Map<Path, Any>,
    entered: MutableList<Pair<Node, Path>>,
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
    callOnEntry(node, parallelPath, event, state._nodeExtensionPoints)
    entered.add(node to parallelPath)
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
    previousNodes: Map<RegionId, Map<Path, Node>>,
    entered: MutableList<Pair<Node, Path>>,
    exited: MutableList<Pair<Node, Path>>,
    unmountedIntermediates: Set<Path>,
  ) {
    // Record SCXML history for every compound flow/region that just left the alive set, keyed by
    // its path → the atomic leaf that was active under it. Done here — after calculateAliveNodes
    // recomputed each region's alive chain but before onExit/prune below — so the read of the
    // now-current alive set is accurate. Rolled back by the transaction snapshot on any later throw.
    recordHistoryOnExit(state, previousAlive)
    // Track lifecycle calls so we can compensate if an exception occurs mid-synchronization.
    // The snapshot rollback in transition() restores structural state; this tracking ensures
    // onEntry/onExit calls remain balanced even when the rollback path is taken.
    //
    // [entered] and [exited] are owned by the caller (transition()) so the runtime pre-mount
    // and pre-unmount steps' entries are folded into the same compensation lists this catch
    // walks. A throw mid-synchronization then exits BOTH pre-mounted intermediates and
    // per-region nodes in the same reversed sweep.
    try {
      // each callOnExit in the two prune loops below is wrapped in runCatching so that one
      // consumer's throwing onExit doesn't skip sibling nodes' onExit calls. Mirrors the
      // cleanDispose pattern (every step runCatching'd, every node gets a chance to clean up
      // its DI scope / coroutine scope / etc). Throws are collected and rethrown — the first
      // throw is the primary, the rest are attached via addSuppressed — after both loops
      // complete. The inner catch below still runs because the rethrow happens before
      // synchronizeNodes returns: it compensates `exited` via callOnEntry (re-enter the
      // partially-exited nodes) and the outer transition catch then snapshot-restores state.
      // `exited.add` always runs even when callOnExit throws, so the compensation re-enters
      // every node we attempted to exit — keeping entry/exit balanced.
      val onExitThrows = mutableListOf<Throwable>()
      // Call onExit for nodes in regions that were pruned
      previousAlive.forEach { (regionId, prevPaths) ->
        if (!state._regions.containsKey(regionId)) {
          val nodes = previousNodes[regionId] ?: emptyMap()
          prevPaths.reversed().forEach { path ->
            nodes[path]?.also {
              runCatching { callOnExit(it, path, event, state._nodeExtensionPoints) }
                .onFailure { onExitThrows.add(it) }
              exited.add(it to path)
            }
          }
        }
      }
      // Per-region synchronization
      state._regions.forEach { (regionId, region) ->
        previousAlive[regionId].orEmpty().reversed().forEach { path ->
          if (!region.alive.contains(path)) {
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
            runCatching { callOnExit(node, path, event, state._nodeExtensionPoints) }
              .onFailure { onExitThrows.add(it) }
            exited.add(node to path)
          }
        }
        region._nodes.keys.retainAll(region.alive.toSet())
      }
      onExitThrows.rethrowAsAggregate()
      // Per-region build loop — entries newly added by calculateAliveNodes get their nodes
      // built and onEntry fired.
      state._regions.forEach { (_, region) ->
        region.alive.forEach { path ->
          if (!region._nodes.containsKey(path)) {
            // Intermediate parallel paths are already built and entered by the pre-mount step
            // (or by materializeRegion during InitEvent). Their node lives in
            // _intermediateParallels and conceptually does NOT belong to any region — but a
            // calling region whose NavigateTo lands beyond the intermediate carries the
            // intermediate's path in its `alive` list (via initParallelAndRouteAbsolute's
            // `resolved[callingRegionId] = parallelPath`). To keep runValidityChecks's
            // alive==nodes invariant satisfied without re-building or re-entering, hand the
            // same ParallelFlowNode instance to the region too.
            val intermediate = state._intermediateParallels[path]
            if (intermediate != null) {
              region._nodes[path] = intermediate.node
              return@forEach
            }
            val pathPayloads = payloads.onBuildPath(path)
            region._nodes[path] =
              nodeBuilder.build(path, pathPayloads, rootSegmentAlias = nodeBuilder.schema.rootSegment)
                .also {
                  callOnEntry(it, path, event, state._nodeExtensionPoints)
                  entered.add(it to path)
                }
          }
        }
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
    } catch (e: Throwable) {
      // Exit nodes that received onEntry and re-enter nodes that received onExit — both will be
      // reconciled by the caller's snapshot restore; this keeps entry/exit balanced meanwhile.
      compensateLifecycle(entered, exited, event, state._nodeExtensionPoints)
      throw e
    }
  }

  fun sendEvent(event: Event) {
    if (isDisposed) return
    if (isDispatching) {
      state._enqueuedEvents.addLast(event)
      return
    }
    var currentEvent: Event = event
    while (true) {
      isDispatching = true
      try {
        val newState = transition(state, currentEvent)
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
      } finally {
        isDispatching = false
      }
      currentEvent = state._enqueuedEvents.removeFirstOrNull() ?: break
      enqueuedEventScheduler?.let { scheduler ->
        scheduler(currentEvent)
        break
      }
    }
  }

  /**
   * Sets a custom scheduler for enqueued events.
   *
   * By default, enqueued events are drained immediately in an iterative loop inside [sendEvent].
   * If you need dispatch to be tied to a platform event loop (e.g. `Handler.post` on Android),
   * set a custom scheduler here. It will be called with the next queued event after each transition;
   * the scheduler is responsible for delivering that event back to [sendEvent] at the right time.
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
    state._history[ancestor] = leaves.distinct()
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

private fun callOnEntry(node: Node, path: Path, event: Event, extensionPoints: List<NodeExtensionPoint>) {
  val snapshot = extensionPoints.toList()
  snapshot.forEach { it.onPreEntry(node, path) }
  // Pass [path] to the new overload; default impl in [Node] delegates to the legacy
  // (event-only) variant for backward compatibility.
  node.onEntry(event, path)
  snapshot.forEach { it.onPostEntry(node, path) }
}

private fun callOnExit(node: Node, path: Path, event: Event, extensionPoints: List<NodeExtensionPoint>) {
  val snapshot = extensionPoints.toList()
  snapshot.forEach { it.onPreExit(node, path) }
  node.onExit(event, path)
  snapshot.forEach { it.onPostExit(node, path) }
}

private fun callOnDispose(node: Node, path: Path, extensionPoints: List<NodeExtensionPoint>) {
  val snapshot = extensionPoints.toList()
  snapshot.forEach { runCatching { it.onPreDispose(node, path) } }
  runCatching { node.onDispose() }
  snapshot.forEach { runCatching { it.onPostDispose(node, path) } }
}
