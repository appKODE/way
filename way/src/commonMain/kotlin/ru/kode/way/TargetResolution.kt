package ru.kode.way

internal fun resolveTransition(
  regions: Map<RegionId, Region>,
  nodeBuilder: NodeBuilder,
  event: Event,
  extensionPoints: List<NodeExtensionPoint>,
  rootNode: Node? = null,
  rootNodePath: Path? = null,
  rootFinishTransitionBuilder: ((Any) -> Transition)? = null,
  intermediateParallels: Map<Path, IntermediateParallel> = emptyMap(),
  history: Map<Path, List<Path>> = emptyMap(),
): ResolvedTransition {
  val regionsResult = regions.entries.fold(ResolvedTransition.EMPTY) { acc, (regionId, region) ->
    // Sub-regions (regions not declared in the root schema) skip Event.Back independently;
    // back dispatch for sub-regions is routed via the parent parallel node's transition(Event.Back).
    // Sub-regions of a parallel-flow ROOT ARE declared in the root schema, yet they must
    // ALSO be skipped here: Back for a root parallel is routed through its transition(Event.Back) in
    // the root-parallel dispatch block below, exactly like a nested parallel. Without this, a single Back
    // is delivered to EVERY root sub-region at once, bypassing the strategy entirely.
    if (event == Event.Back) {
      val isRootParallelSubRegion = rootNode is ParallelFlowNode<*> && rootNodePath != null &&
        regionId.path != rootNodePath && regionId.path.startsWith(rootNodePath)
      if (!nodeBuilder.schema.regions.contains(regionId) || isRootParallelSubRegion) {
        return@fold acc
      }
    }
    // Targeted RootFinishRequestEvent: only the named region handles it; other regions contribute
    // nothing. Skipping here also prevents the internal event from being delivered to user
    // Node.transition implementations or NodeExtensionPoint.onPreTransition hooks in unrelated regions.
    if (event is RootFinishRequestEvent && event.targetRegionId != null && event.targetRegionId != regionId) {
      return@fold acc
    }
    val node = region.nodes[region.active] ?: error("expected node to exist at path \"${region.active}\"")
    val transition = if (event is RootFinishRequestEvent) {
      region._rootFinishTransitionBuilder(event.result)
    } else {
      buildTransition(event, node, region.active, extensionPoints)
    }
    val resolved = resolveTransitionInRegion(
      regionId = regionId,
      transition,
      path = region.active,
      activePath = region.active,
      nodes = region.nodes,
      nodeBuilder = nodeBuilder,
      event = event,
      extensionPoints = extensionPoints,
      allRegions = regions,
      history = history,
    )
    acc + resolved
  }
  // Intermediate parallels (parallel-flow nodes that sit ABOVE the runtime regions but BELOW the
  // root because their inner schema is itself parallel-rooted). They own no region of their own,
  // so the region-fold above skips them — yet a leaf sub-region's Finish bubbles into a typed
  // `ChildFinishRequest` for the NEAREST enclosing parallel, which is often one of these
  // intermediates, NOT the root parallel. Dispatch the event through each intermediate so its
  // `transition()` can observe and respond.
  //
  // Deepest-first ordering: a Finish from an intermediate produces ANOTHER EnqueueEvent that
  // drains in a later sendEvent cycle, so within this single cycle the ordering between
  // intermediates vs root is observationally irrelevant for non-Finish results. Deepest-first
  // is chosen as the conventional, predictable order.
  //
  // Same skip guards as the root-parallel dispatch below: InitEvent is consumed during the
  // parallel-root init branch in NavigationService.kt; RootFinishRequestEvent is targeted at a
  // specific region via `event.targetRegionId` and consumed there. Event.Back is ALSO skipped: Back
  // routing through every parallel (root, intermediate, or nested) flows uniformly through
  // dispatchBackThroughParallel — entered from the root-parallel block below or from
  // maybeResolveBackEvent — which recurses into intermediate/nested parallels and consults each
  // one's transition(Event.Back) exactly once. Dispatching Back through the fold as well would ask
  // an intermediate's transition twice and double-route a DispatchBackTo.
  val intermediatesResult = if (event is InitEvent || event is RootFinishRequestEvent || event == Event.Back) {
    regionsResult
  } else {
    intermediateParallels.entries
      .sortedByDescending { it.key.length }
      .fold(regionsResult) { acc, (path, intermediate) ->
        val resolved = resolveParallelTransition(
          parallelNode = intermediate.node,
          parallelNodePath = path,
          finishTransitionBuilder = intermediate.finishBuilder,
          event = event,
          extensionPoints = extensionPoints,
          nodeBuilder = nodeBuilder,
          allRegions = regions,
          history = history,
        )
        acc + resolved
      }
  }
  // Top-level parallel-rooted schema: when the root is a ParallelFlowNode it owns no runtime region
  // (sub-regions live ONE segment deeper), so events that bubble past every sub-region — most
  // notably the ChildFinishRequest enqueued by computeSubRegionFinishBuilder when a sub-region
  // emits Finish — never reach the parent parallel's `transition()` via the fold above. Dispatch
  // them here so the parent parallel-flow can observe and react to its own sub-regions' lifecycle.
  // InitEvent is excluded (the root parallel's InitEvent fires during the parallel-root init
  // branch in NavigationService.kt). RootFinishRequestEvent is excluded because it is already
  // targeted at a specific region via `event.targetRegionId` and consumed there.
  if (rootNode is ParallelFlowNode<*> && rootNodePath != null && event !is InitEvent &&
    event !is RootFinishRequestEvent
  ) {
    val rootResolved = if (event == Event.Back) {
      // Root parallel Back: consult the root's own transition(Event.Back) and route to exactly ONE
      // sub-region, mirroring how nested/intermediate parallels resolve Back via
      // maybeResolveBackEvent → dispatchBackThroughParallel. The fold above skipped every root
      // sub-region for Back, so this is the sole Back dispatch for a parallel-rooted schema.
      dispatchBackThroughParallel(
        parallelNode = rootNode,
        parallelNodePath = rootNodePath,
        subRegionActivePaths = subRegionActivePaths(rootNodePath, regions),
        allRegions = regions,
        nodeBuilder = nodeBuilder,
        event = event,
        extensionPoints = extensionPoints,
        finishTransitionBuilder = rootFinishTransitionBuilder,
        history = history,
      )
    } else {
      resolveParallelTransition(
        parallelNode = rootNode,
        parallelNodePath = rootNodePath,
        finishTransitionBuilder = rootFinishTransitionBuilder,
        event = event,
        extensionPoints = extensionPoints,
        nodeBuilder = nodeBuilder,
        allRegions = regions,
        history = history,
      )
    }
    return intermediatesResult + rootResolved
  }
  return intermediatesResult
}

/**
 * Dispatches [event] through a [ParallelFlowNode] (top-level root OR an intermediate that sits
 * above the runtime regions) and translates the resulting [FlowTransition] into a
 * [ResolvedTransition]. Handles every case that makes sense for a parallel on a non-Back event:
 * [Ignore], [Stay], [EnqueueEvent], [Finish], [NavigateTo], and the [NavigateAndEnqueue] wrapper
 * around them. Back is routed separately via [dispatchBackThroughParallel].
 */
private fun resolveParallelTransition(
  parallelNode: ParallelFlowNode<*>,
  parallelNodePath: Path,
  finishTransitionBuilder: ((Any) -> Transition)?,
  event: Event,
  extensionPoints: List<NodeExtensionPoint>,
  nodeBuilder: NodeBuilder,
  allRegions: Map<RegionId, Region>,
  history: Map<Path, List<Path>> = emptyMap(),
): ResolvedTransition {
  val transition = buildTransition(event, parallelNode, parallelNodePath, extensionPoints)
  return resolveParallelInner(
    parallelNode = parallelNode,
    parallelNodePath = parallelNodePath,
    finishTransitionBuilder = finishTransitionBuilder,
    transition = transition,
    event = event,
    extensionPoints = extensionPoints,
    nodeBuilder = nodeBuilder,
    allRegions = allRegions,
    history = history,
  )
}

private fun resolveParallelInner(
  parallelNode: ParallelFlowNode<*>,
  parallelNodePath: Path,
  finishTransitionBuilder: ((Any) -> Transition)?,
  transition: Transition,
  event: Event,
  extensionPoints: List<NodeExtensionPoint>,
  nodeBuilder: NodeBuilder,
  allRegions: Map<RegionId, Region>,
  history: Map<Path, List<Path>> = emptyMap(),
): ResolvedTransition = when (transition) {
  is Ignore, is Stay -> ResolvedTransition.EMPTY

  is EnqueueEvent -> ResolvedTransition(
    targetPaths = emptyMap(),
    payloads = emptyMap(),
    enqueuedEvents = listOf(transition.event),
  )

  is NavigateAndEnqueue -> {
    val inner = resolveParallelInner(
      parallelNode = parallelNode,
      parallelNodePath = parallelNodePath,
      finishTransitionBuilder = finishTransitionBuilder,
      transition = transition.navigate,
      event = event,
      extensionPoints = extensionPoints,
      nodeBuilder = nodeBuilder,
      allRegions = allRegions,
      history = history,
    )
    inner.copy(enqueuedEvents = (inner.enqueuedEvents.orEmpty() + transition.events))
  }

  is Finish<*> -> {
    // For a root parallel `R` is bound to the service's `R`, so its Finish bubbles to
    // `onFinishRequest`; for an intermediate parallel the builder enqueues a typed
    // `ChildFinishRequest` for the enclosing parent parallel via `computeSubRegionFinishBuilder`.
    val nextTransition = finishTransitionBuilder?.invoke(transition.result) ?: Ignore
    resolveParallelInner(
      parallelNode = parallelNode,
      parallelNodePath = parallelNodePath,
      finishTransitionBuilder = finishTransitionBuilder,
      transition = nextTransition,
      event = event,
      extensionPoints = extensionPoints,
      nodeBuilder = nodeBuilder,
      allRegions = allRegions,
      history = history,
    )
  }

  // DispatchBackTo is only meaningful on Event.Back, which is routed by dispatchBackThroughParallel
  // (never through this function). Returning it for any other event is a no-op consume.
  is DispatchBackTo -> ResolvedTransition.EMPTY

  is NavigateTo -> {
    // A parallel has no region of its own and no "active sub-schema" to anchor schema-relative
    // resolution. We classify each target and dispatch through the appropriate context:
    //
    // - AbsoluteTarget: resolved via `allRegions.keys`, no schema-relative context needed.
    //   Delegate through a synthetic single-node region (parallelPath -> parallelNode).
    //   Intermediates needed by AbsoluteTarget paths under unmaterialized parallel-rooted
    //   sub-regions are mounted by NavigationService.transition's pre-mount step before
    //   calculateAliveNodes runs.
    // - FlowTarget / ScreenTarget: schema-relative. Resolve against the FIRST declared sub-region
    //   so `resolveAbsoluteTargetPath` lands in a real sub-schema rather than the parallel's parent
    //   schema with a `regions.first()` fallback that silently misroutes. Target a specific
    //   sub-region explicitly with AbsoluteTarget instead.
    val syntheticRegionId = RegionId(parallelNodePath)
    // All descendant regions at any depth under the parallel (not just immediate children — cf.
    // subRegionActivePaths, which restricts to immediate children).
    val descendantRegionIds = allRegions.keys
      .filter { it.path.startsWith(parallelNodePath) && it.path != parallelNodePath }
      .toSet()
    val targetPaths = LinkedHashMap<RegionId, Path>()
    val payloads = mutableMapOf<Path, Any>()
    val enqueued = mutableListOf<Event>()
    transition.targets.forEach { target ->
      val singleTargetTransition = NavigateTo(target)
      val resolved = when (target) {
        // AbsoluteTarget and HistoryTarget both carry an ABSOLUTE path, so they need no
        // schema-relative anchor — resolveTransitionInRegion locates the owning region from
        // allRegions. Delegate both through a synthetic single-node region.
        is AbsoluteTarget, is HistoryTarget -> resolveTransitionInRegion(
          regionId = syntheticRegionId,
          transition = singleTargetTransition,
          path = parallelNodePath,
          activePath = parallelNodePath,
          nodes = mapOf(parallelNodePath to parallelNode),
          nodeBuilder = nodeBuilder,
          event = event,
          extensionPoints = extensionPoints,
          allRegions = allRegions,
          history = history,
        )

        is FlowTarget, is ScreenTarget -> {
          val firstSubRegion = descendantRegionIds.firstOrNull()
            ?: error(
              "NavigateTo(FlowTarget/ScreenTarget) from a ParallelFlowNode requires at least one " +
                "alive sub-region; none found at $parallelNodePath",
            )
          val firstActive = allRegions[firstSubRegion]?.active ?: firstSubRegion.path
          val firstNodes = allRegions[firstSubRegion]?.nodes ?: emptyMap()
          resolveTransitionInRegion(
            regionId = firstSubRegion,
            transition = singleTargetTransition,
            path = firstSubRegion.path,
            activePath = firstActive,
            nodes = firstNodes,
            nodeBuilder = nodeBuilder,
            event = event,
            extensionPoints = extensionPoints,
            allRegions = allRegions,
            history = history,
          )
        }
      }
      targetPaths.putAll(resolved.targetPaths)
      payloads.putAll(resolved.payloads)
      resolved.enqueuedEvents?.let { enqueued.addAll(it) }
    }
    ResolvedTransition(
      targetPaths = targetPaths,
      payloads = payloads,
      enqueuedEvents = enqueued.takeIf { it.isNotEmpty() },
    )
  }
}

/**
 * @param path A path relative to which resolution happens
 * @param activePath A path which was last active in navigation state
 */
private fun resolveTransitionInRegion(
  regionId: RegionId,
  transition: Transition,
  path: Path,
  activePath: Path,
  nodes: Map<Path, Node>,
  nodeBuilder: NodeBuilder,
  event: Event,
  extensionPoints: List<NodeExtensionPoint>,
  allRegions: Map<RegionId, Region>,
  history: Map<Path, List<Path>> = emptyMap(),
): ResolvedTransition = when (transition) {
  is EnqueueEvent -> ResolvedTransition(
    targetPaths = mapOf(regionId to activePath),
    payloads = emptyMap(),
    enqueuedEvents = listOf(transition.event),
  )

  is NavigateAndEnqueue -> {
    // Resolve the inner NavigateTo through the normal path, then append the follow-up events
    // to its enqueuedEvents list. Reuses the existing target-resolution logic so multi-target,
    // parallel-init, and payload handling all work identically.
    val navigateResult = resolveTransitionInRegion(
      regionId, transition.navigate, path, activePath, nodes, nodeBuilder,
      event, extensionPoints, allRegions, history,
    )
    val combinedEvents = (navigateResult.enqueuedEvents ?: emptyList()) + transition.events
    navigateResult.copy(enqueuedEvents = combinedEvents)
  }

  is DispatchBackTo -> {
    // A parallel returned DispatchBackTo for a Back that reached it here (rather than through the
    // dedicated dispatchBackThroughParallel entry). Route Back into the requested sub-region,
    // soft-falling-back to the deepest active one when the id is stale/unresolved.
    val subRegions = subRegionActivePaths(path, allRegions)
    check(subRegions.isNotEmpty()) {
      "DispatchBackTo must be returned from a ParallelFlowNode's transition(Event.Back) (path=$path)"
    }
    val chosen = chooseBackRegion(transition.regionId, subRegions)
    dispatchBackIntoRegion(chosen, subRegions, allRegions, nodeBuilder, event, extensionPoints, history)
  }

  is NavigateTo -> {
    val schema = nodeBuilder.schema
    val targetPaths = LinkedHashMap<RegionId, Path>()
    val payloads = mutableMapOf<Path, Any>()
    // Tracks every path initialized either before this NavigateTo started (pre-existing regions) or
    // by an earlier target in the same NavigateTo. Lifted out of the per-target loop so a later
    // AbsoluteTarget into a sibling sub-region of a cold parallel does not re-run
    // initParallelAndRouteAbsolute and reset siblings produced by an earlier target.
    val initializedPaths = allRegions.keys.mapTo(mutableSetOf()) { it.path }
    transition.targets.forEach { target ->
      val resolved = when (target) {
        is AbsoluteTarget -> resolveAbsoluteTarget(
          target,
          regionId,
          allRegions,
          nodeBuilder,
          schema,
          payloads,
          initializedPaths,
          alreadyChosen = targetPaths,
        )

        is FlowTarget, is ScreenTarget -> {
          val targetPathAbs = resolveAbsoluteTargetPath(schema, path, target.path)
          target.payload?.also { payloads[targetPathAbs] = it }
          maybeResolveInitial(target, targetPathAbs, nodeBuilder, nodes, schema, payloads, regionId)
        }

        is HistoryTarget -> resolveHistoryTarget(
          target, regionId, nodes, allRegions, nodeBuilder, schema, payloads, history, initializedPaths,
          alreadyChosen = targetPaths,
        )
      }
      targetPaths.putAll(resolved)
    }
    ResolvedTransition(
      targetPaths = targetPaths,
      payloads = payloads,
      enqueuedEvents = null,
    )
  }

  is Finish<*> -> {
    val schema = nodeBuilder.schema
    val finishingFlowPath = findParentFlowPathInclusive(schema, path)
    val finishEvent = if (finishingFlowPath.isRootInRegion(regionId)) {
      RootFinishRequestEvent(transition.result, targetRegionId = regionId)
    } else {
      val parentFlowSchemaWithPath = findParentSchema(schema, finishingFlowPath, inclusive = false)
      val relativePath = finishingFlowPath
        .relativeToSchema(parentFlowSchemaWithPath.path)
        .reRootAt(parentFlowSchemaWithPath.schema.rootSegment)
      parentFlowSchemaWithPath.schema.createChildFlowFinishRequestEvent(
        findOwningRegionIdOrThrow(parentFlowSchemaWithPath.schema.regions, relativePath),
        relativePath,
        transition.result,
      )
    }
    ResolvedTransition(
      targetPaths = mapOf(regionId to activePath),
      payloads = emptyMap(),
      enqueuedEvents = listOf(finishEvent),
    )
  }

  is Stay -> {
    ResolvedTransition(
      targetPaths = mapOf(regionId to activePath),
      payloads = emptyMap(),
      enqueuedEvents = null,
    )
  }

  is Ignore -> {
    if (path.isRootInRegion(regionId)) {
      val resolved = maybeResolveBackEvent(
        regionId,
        activePath,
        nodes,
        nodeBuilder,
        event,
        extensionPoints,
        allRegions,
        history,
      )
      if (resolved == null) {
        ResolvedTransition(
          targetPaths = emptyMap(),
          payloads = emptyMap(),
          enqueuedEvents = null,
        )
      } else {
        resolved
      }
    } else {
      val parentPath = path.dropLast(1)
      val node = nodes[parentPath] ?: error("expected node to exist at path \"${parentPath}\"")
      resolveTransitionInRegion(
        regionId,
        buildTransition(event, node, parentPath, extensionPoints),
        parentPath,
        activePath,
        nodes,
        nodeBuilder,
        event,
        extensionPoints,
        allRegions,
        history,
      )
    }
  }
}

/**
 * NavigateTo(AbsoluteTarget) handler: records the target's payloads and resolves its absolute path
 * into the owning region via [resolveAbsoluteLeaves]. Rejects a path that points directly at a
 * ParallelFlowNode — targeting one would create an orphan Region in calculateAliveNodes (via
 * getOrPut); for state.rootNode this then triggers a duplicate build in synchronizeNodes' per-region
 * build loop (state.rootNode is not in _intermediateParallels, so the intermediate-reuse branch
 * doesn't fire and a second ParallelFlowNode instance is built under region._nodes), desyncing the
 * instance Compose observes from the one the runtime queries.
 */
private fun resolveAbsoluteTarget(
  target: AbsoluteTarget,
  callingRegionId: RegionId,
  allRegions: Map<RegionId, Region>,
  nodeBuilder: NodeBuilder,
  schema: Schema,
  payloads: MutableMap<Path, Any>,
  initializedPaths: MutableSet<Path>,
  alreadyChosen: Map<RegionId, Path>,
): Map<RegionId, Path> {
  payloads.putAll(target.payloads)
  val absolutePath = target.path
  require(!isParallelFlowAt(schema, absolutePath)) {
    "NavigateTo(AbsoluteTarget(\"$absolutePath\")) targets a ParallelFlowNode path. " +
      "Use an AbsoluteTarget pointing at a path inside one of its sub-regions."
  }
  return resolveAbsoluteLeaves(
    candidates = listOf(absolutePath),
    defaultRegionId = callingRegionId,
    fallbackNodes = emptyMap(),
    allRegions = allRegions,
    nodeBuilder = nodeBuilder,
    schema = schema,
    payloads = payloads,
    initializedPaths = initializedPaths,
    alreadyChosenBase = alreadyChosen,
  )
}

/**
 * NavigateTo(HistoryTarget) handler. `target.path` is ABSOLUTE (the flow/region whose history to
 * restore); its owning region is located exactly as an AbsoluteTarget's, falling back to the calling
 * region (with its nodes as the build context) when the flow isn't currently materialized. Then:
 * - nothing recorded (first visit) → default initial, identical to `FlowTarget(flowPath)`;
 * - deep → restore every recorded atomic leaf via [resolveAbsoluteLeaves], so a leaf belonging to a
 *   parallel sub-region lands in its OWN region (re-materializing an intermediate parallel when the
 *   flow was fully torn down) rather than collapsing every leaf into one region;
 * - shallow → project the recorded leaves to their distinct immediate children and resolve each like
 *   an AbsoluteTarget, re-materializing a cold parallel and fanning out ALL its region roots at their
 *   defaults (a single-region flow has one immediate child, reducing to the plain initial case).
 */
private fun resolveHistoryTarget(
  target: HistoryTarget,
  callingRegionId: RegionId,
  nodes: Map<Path, Node>,
  allRegions: Map<RegionId, Region>,
  nodeBuilder: NodeBuilder,
  schema: Schema,
  payloads: MutableMap<Path, Any>,
  history: Map<Path, List<Path>>,
  initializedPaths: MutableSet<Path>,
  alreadyChosen: Map<RegionId, Path>,
): Map<RegionId, Path> {
  val flowPath = target.path
  val targetRegionId = owningRegionId(flowPath, allRegions.keys, fallback = callingRegionId)
  val existingNodes = allRegions[targetRegionId]?.nodes ?: nodes
  target.payload?.also { payloads[flowPath] = it }
  val recorded = history[flowPath]
  val resolved = when {
    recorded.isNullOrEmpty() ->
      maybeResolveInitial(flowPath, targetRegionId, nodeBuilder, existingNodes, schema, payloads)

    target.deep -> resolveAbsoluteLeaves(
      candidates = recorded,
      defaultRegionId = targetRegionId,
      fallbackNodes = existingNodes,
      allRegions = allRegions,
      nodeBuilder = nodeBuilder,
      schema = schema,
      payloads = payloads,
      initializedPaths = initializedPaths,
      alreadyChosenBase = alreadyChosen,
    )

    else -> resolveAbsoluteLeaves(
      candidates = recorded.map { it.take(flowPath.length + 1) }.distinct(),
      defaultRegionId = targetRegionId,
      fallbackNodes = existingNodes,
      allRegions = allRegions,
      nodeBuilder = nodeBuilder,
      schema = schema,
      payloads = payloads,
      initializedPaths = initializedPaths,
      alreadyChosenBase = alreadyChosen,
    )
  }
  resolved.keys.forEach { initializedPaths.add(it.path) }
  return resolved
}

private fun resolveAbsoluteTargetPath(schema: Schema, activePath: Path, targetPath: Path): Path {
  val (activeSchema, activeSchemaPath) = findParentSchema(schema, activePath, inclusive = true)
  val regionId = owningRegionInSchema(activeSchema, activeSchemaPath, activePath)
  val relativeResolvedPath = activeSchema.target(regionId, targetPath.lastSegment())
    ?: error(
      "failed to resolve target \"$targetPath\" from path \"$activePath\": " +
        "targets from ScreenNode transitions must be siblings within the same parent flow schema",
    )
  // `schema.target` returns a path anchored at the schema's `rootSegment` (its first segment),
  // and — for parallel-rooted regions — its second segment is the regionRoot. `absoluteRegionRoot`
  // already covers BOTH (schemaRoot + regionRoot, when the region path is longer than 1), so we
  // drop exactly the same number of leading segments from the resolved relative path before
  // appending. Dropping fewer would double-stack the regionRoot (`...par05Alpha.par05Alpha...`);
  // dropping more would skip a real intermediate segment.
  return absoluteRegionRoot(activeSchemaPath, regionId).append(relativeResolvedPath.drop(regionId.path.length))
}

internal fun absoluteRegionRoot(schemaPath: Path, relativeRegionId: RegionId): Path =
  if (relativeRegionId.path.length <= 1) {
    schemaPath
  } else {
    schemaPath.append(relativeRegionId.path.drop(1))
  }

internal fun computeSubRegionFinishBuilder(schema: Schema, regionId: RegionId): (Any) -> Transition {
  val absPath = regionId.path
  if (absPath.length <= 1) return { Ignore }
  val parallelNodePath = absPath.dropLast(1)
  val (parallelSchema, parallelSchemaPath) = findParentSchema(schema, parallelNodePath, inclusive = true)
  val relativeRegionId = parallelSchema.regions.find { relRegionId ->
    absoluteRegionRoot(parallelSchemaPath, relRegionId) == absPath
  } ?: return { Ignore }
  val subRegionRootPath = Path(absPath.lastSegment())
  return { result: Any ->
    EnqueueEvent(parallelSchema.createChildFlowFinishRequestEvent(relativeRegionId, subRegionRootPath, result))
  }
}

data class SchemaWithPath(val schema: Schema, val path: Path)

/**
 * Searches for a schema which contains the specified [path], search starts from the [root].
 *
 * @param root a schema to start search from
 * @param path a path relative to the root schema
 * @param inclusive when `true` and last segment is a flow node with a schema, will return this schema, otherwise
 * will return its parent. For example, for path "appFlow.loginFlow":
 * - `inclusive == true` will return schema of the loginFlow
 * - `inclusive == false` will return schema of the appFlow
 */
internal fun findParentSchema(root: Schema, path: Path, inclusive: Boolean): SchemaWithPath {
  check(root.rootSegment == path.firstSegment()) {
    "path first segment must match schema rootSegment, " +
      "but \"${path.firstSegment().id}\" != \"${root.rootSegment.id}\""
  }
  // Search works like this:
  // Given the path "appFlow.intro.loginFlow.credentials.mainFlow.profile
  // - appFlow schema is "root", assign it to "activeSchema"
  // - search for "intro" child schema in "activeSchema" -> fails, "intro" is a screen node
  // - search for "loginFlow" child schema in "activeSchema" -> success, assign it to "activeSchema"
  // - search for "credentials" in "activeSchema" -> fail
  // - search for "mainFlow" in "activeSchema" -> success, assign it to "activeSchema"
  // - result: activeSchema is "mainFlow" schema
  var activeSchema: Schema = root
  // for "appFlow.screen1" activeSchemaSegmentIndex = 0 (appFlow)
  // for "appFlow.screen1.loginFlow" activeSchemaSegmentIndex = 2 (loginFlow)
  var activeSchemaSegmentIndex = 0
  path.segments
    .drop(1)
    .let { segments -> if (inclusive) segments else segments.dropLast(1) }
    .forEachIndexed { index, segment ->
      val child = activeSchema.childSchemas.entries.find { (s, _) -> s == segment }?.value
      if (child != null) {
        activeSchema = child
        activeSchemaSegmentIndex = index + 1
      }
    }
  val activeSchemaPath = path.take(activeSchemaSegmentIndex + 1)
  return SchemaWithPath(
    schema = activeSchema,
    path = activeSchemaPath,
  )
}

private fun maybeResolveBackEvent(
  regionId: RegionId,
  activePath: Path,
  nodes: Map<Path, Node>,
  nodeBuilder: NodeBuilder,
  event: Event,
  extensionPoints: List<NodeExtensionPoint>,
  allRegions: Map<RegionId, Region>,
  history: Map<Path, List<Path>> = emptyMap(),
): ResolvedTransition? {
  if (event != Event.Back || activePath.segments.size <= 1) return null

  // A path with direct child entries in allRegions IS a parallel node (allRegions is the
  // authoritative runtime state — no schema traversal needed). Such a parallel is an ordinary active
  // node of `regionId` (a flow region), not a sub-region root, so we thread its region + nodes:
  // a non-Ignore/non-DispatchBackTo transition it returns (e.g. Finish) is then routed through
  // resolveTransitionInRegion's schema-based arms, and finishTransitionBuilder is unused on this path.
  fun dispatchBackIfParallelAt(candidatePath: Path): ResolvedTransition? {
    val subRegions = subRegionActivePaths(candidatePath, allRegions)
    if (subRegions.isEmpty()) return null
    val parallelNode = nodes[candidatePath] as? ParallelFlowNode<*>
      ?: error("no parallel node at path $candidatePath")
    return dispatchBackThroughParallel(
      parallelNode = parallelNode,
      parallelNodePath = candidatePath,
      subRegionActivePaths = subRegions,
      allRegions = allRegions,
      nodeBuilder = nodeBuilder,
      event = event,
      extensionPoints = extensionPoints,
      finishTransitionBuilder = null,
      ownerRegionId = regionId,
      ownerNodes = nodes,
      history = history,
    )
  }

  val parentPath = activePath.dropLast(1)
  // Try the active node itself first (it may be a parallel), then its parent.
  dispatchBackIfParallelAt(activePath)?.let { return it }
  dispatchBackIfParallelAt(parentPath)?.let { return it }

  val transition = when (val parentNode = nodes[parentPath] ?: error("no node at path $parentPath")) {
    is FlowNode<*> -> Finish(parentNode.dismissResult)

    is ScreenNode -> NavigateTo(ScreenTarget(parentPath))

    is ParallelFlowNode<*> -> return ResolvedTransition(
      targetPaths = mapOf(regionId to parentPath),
      payloads = emptyMap(),
      enqueuedEvents = null,
    )
  }

  return resolveTransitionInRegion(
    regionId, transition, activePath, activePath,
    nodes, nodeBuilder, Event.Back, extensionPoints, allRegions, history,
  )
}

/**
 * Dispatches a Back event through a [ParallelFlowNode] by consulting its own `transition(Event.Back)`
 * exactly once:
 * - [DispatchBackTo] `(r)` → route Back into region `r` (schema-local ids normalized via
 *   [resolveRegionId]; stale/unresolved ids soft-fall-back to [deepestRegion] — Back never throws).
 * - [Ignore] → route Back into [deepestRegion].
 * - any other transition (Stay / Finish / NavigateTo / EnqueueEvent / …) → apply it to the parallel
 *   itself via [resolveParallelInner], exactly as a non-Back event would be handled.
 *
 * Routing into a region means [dispatchBackIntoRegion] walks up from that region's active node
 * asking each node's [transition][Node.transition] in turn — identical to how flow Back propagates —
 * and recurses through [dispatchBackThroughParallel] when the chosen leaf is itself a nested parallel.
 */
private fun dispatchBackThroughParallel(
  parallelNode: ParallelFlowNode<*>,
  parallelNodePath: Path,
  subRegionActivePaths: Map<RegionId, Path>,
  allRegions: Map<RegionId, Region>,
  nodeBuilder: NodeBuilder,
  event: Event,
  extensionPoints: List<NodeExtensionPoint>,
  finishTransitionBuilder: ((Any) -> Transition)?,
  // When this parallel is an ordinary active node of a flow region (the two maybeResolveBackEvent
  // entries), its owning region + nodes are threaded in so a non-Ignore/non-DispatchBackTo transition
  // is resolved through resolveTransitionInRegion — see the else branch below. Null for the
  // root/sub-region-root entries, which own an explicit finishTransitionBuilder and fan out via
  // resolveParallelInner.
  ownerRegionId: RegionId? = null,
  ownerNodes: Map<Path, Node>? = null,
  history: Map<Path, List<Path>> = emptyMap(),
): ResolvedTransition {
  val parallelBackTransition = buildTransition(event, parallelNode, parallelNodePath, extensionPoints)
  val chosenRegionId = when (parallelBackTransition) {
    is DispatchBackTo -> chooseBackRegion(parallelBackTransition.regionId, subRegionActivePaths)

    is Ignore -> deepestRegion(subRegionActivePaths)

    // A flow-nested parallel is not a sub-region root, so its own transition must be resolved exactly
    // as the region fold would: resolveTransitionInRegion derives the schema-based child/root finish
    // for a Finish (no finishTransitionBuilder involved). In practice a pure transition() only reaches
    // this arm as Ignore (handled above); this keeps Finish/NavigateTo/… correct-by-construction for a
    // stateful transition() that returns something else on re-consultation.
    else -> return if (ownerRegionId != null && ownerNodes != null) {
      resolveTransitionInRegion(
        regionId = ownerRegionId,
        transition = parallelBackTransition,
        path = parallelNodePath,
        activePath = parallelNodePath,
        nodes = ownerNodes,
        nodeBuilder = nodeBuilder,
        event = event,
        extensionPoints = extensionPoints,
        allRegions = allRegions,
        history = history,
      )
    } else {
      resolveParallelInner(
        parallelNode = parallelNode,
        parallelNodePath = parallelNodePath,
        finishTransitionBuilder = finishTransitionBuilder,
        transition = parallelBackTransition,
        event = event,
        extensionPoints = extensionPoints,
        nodeBuilder = nodeBuilder,
        allRegions = allRegions,
        history = history,
      )
    }
  }
  return dispatchBackIntoRegion(
    chosenRegionId,
    subRegionActivePaths,
    allRegions,
    nodeBuilder,
    event,
    extensionPoints,
    history,
  )
}

/**
 * Dispatches Back into the chosen sub-region's active leaf. When that leaf is itself a nested
 * parallel, recurses through [dispatchBackThroughParallel] so the nested parallel's own
 * `transition(Event.Back)` is consulted; otherwise walks up via [resolveTransitionInRegion] exactly
 * like flow Back.
 */
private fun dispatchBackIntoRegion(
  chosenRegionId: RegionId,
  subRegionActivePaths: Map<RegionId, Path>,
  allRegions: Map<RegionId, Region>,
  nodeBuilder: NodeBuilder,
  event: Event,
  extensionPoints: List<NodeExtensionPoint>,
  history: Map<Path, List<Path>> = emptyMap(),
): ResolvedTransition {
  val chosenPath = subRegionActivePaths[chosenRegionId]
    ?: error("chosen RegionId \"${chosenRegionId.path}\" is not among active sub-regions")
  val chosenNodes = allRegions[chosenRegionId]?.nodes ?: emptyMap()
  val chosenActiveNode = chosenNodes[chosenPath]
    ?: error("no node at active path \"$chosenPath\" in region $chosenRegionId")
  if (chosenActiveNode is ParallelFlowNode<*>) {
    val nestedSubRegions = subRegionActivePaths(chosenPath, allRegions)
    if (nestedSubRegions.isNotEmpty()) {
      return dispatchBackThroughParallel(
        parallelNode = chosenActiveNode,
        parallelNodePath = chosenPath,
        subRegionActivePaths = nestedSubRegions,
        allRegions = allRegions,
        nodeBuilder = nodeBuilder,
        event = event,
        extensionPoints = extensionPoints,
        // chosenPath IS a sub-region root here, so a Finish the nested parallel returns bubbles as a
        // typed ChildFinishRequest to its enclosing parallel.
        finishTransitionBuilder = computeSubRegionFinishBuilder(nodeBuilder.schema, chosenRegionId),
        history = history,
      )
    }
  }
  return resolveTransitionInRegion(
    regionId = chosenRegionId,
    transition = buildTransition(event, chosenActiveNode, chosenPath, extensionPoints),
    path = chosenPath,
    activePath = chosenPath,
    nodes = chosenNodes,
    nodeBuilder = nodeBuilder,
    event = event,
    extensionPoints = extensionPoints,
    allRegions = allRegions,
    history = history,
  )
}

private fun subRegionActivePaths(parentPath: Path, allRegions: Map<RegionId, Region>): Map<RegionId, Path> = allRegions
  .filterKeys { it.path.length == parentPath.length + 1 && it.path.startsWith(parentPath) }
  .mapValues { it.value.active }

private fun buildTransition(
  event: Event,
  node: Node,
  path: Path,
  extensionPoints: List<NodeExtensionPoint>,
): Transition = if (event is InitEvent) {
  when (node) {
    is FlowNode<*> -> {
      NavigateTo(node.initial)
    }

    is ParallelFlowNode<*> -> Stay

    is ScreenNode -> {
      error("initial event is expected to be received on flow node only")
    }
  }
} else {
  val snapshot = extensionPoints.toList()
  snapshot.forEach { it.onPreTransition(node, path, event) }
  // The when is required even though every arm reads `node.transition(event)`: transition() is
  // declared per Node subtype (FlowNode/ParallelFlowNode → FlowTransition, ScreenNode →
  // ScreenTransition), NOT on the Node base — collapsing the arms would not resolve the overload.
  when (node) {
    is FlowNode<*> -> {
      node.transition(event)
    }

    is ParallelFlowNode<*> -> {
      node.transition(event)
    }

    is ScreenNode -> {
      node.transition(event)
    }
  }.also { transition ->
    snapshot.forEach { it.onPostTransition(node, path, event, transition) }
  }
}

internal fun maybeResolveInitial(
  target: Target,
  targetPathAbs: Path,
  nodeBuilder: NodeBuilder,
  nodes: Map<Path, Node>,
  schema: Schema,
  payloads: MutableMap<Path, Any>,
  callingRegionId: RegionId,
  visitedPaths: MutableSet<Path> = mutableSetOf(),
): Map<RegionId, Path> = when (target) {
  is ScreenTarget -> {
    // mirror the Path overload's cycle guard so a future chain that re-arrives at the same
    // absolute path through a ScreenTarget hop is caught the same way the FlowTarget path is.
    // Defensive: the practical reach for this through standard schemas is low (FlowNode.initial
    // chains via ScreenTarget always terminate immediately), but threading the same visitedPaths
    // set through every arm keeps the invariant uniform and future-proof.
    check(targetPathAbs !in visitedPaths) {
      "cycle detected in FlowNode.initial chain at \"$targetPathAbs\" via ScreenTarget; " +
        "visited paths: ${visitedPaths.map { it.toString() }}"
    }
    visitedPaths.add(targetPathAbs)
    mapOf(callingRegionId to targetPathAbs)
  }

  is FlowTarget -> {
    maybeResolveInitial(targetPathAbs, callingRegionId, nodeBuilder, nodes, schema, payloads, visitedPaths)
  }

  is AbsoluteTarget -> {
    error(
      "AbsoluteTarget is not supported as FlowNode.initial. " +
        "Use ScreenTarget or FlowTarget for the initial navigation target.",
    )
  }

  is HistoryTarget -> {
    error(
      "HistoryTarget is not supported as FlowNode.initial. " +
        "Use ScreenTarget or FlowTarget for the initial navigation target.",
    )
  }
}

private fun maybeResolveInitial(
  targetPathAbs: Path,
  callingRegionId: RegionId,
  nodeBuilder: NodeBuilder,
  nodes: Map<Path, Node>,
  schema: Schema,
  payloads: MutableMap<Path, Any>,
  visitedPaths: MutableSet<Path> = mutableSetOf(),
): Map<RegionId, Path> {
  check(targetPathAbs !in visitedPaths) {
    "cycle detected in FlowNode.initial chain at \"$targetPathAbs\"; visited paths: ${visitedPaths.map {
      it.toString()
    }}"
  }
  visitedPaths.add(targetPathAbs)
  val targetNode = nodes.getOrElse(targetPathAbs) {
    nodeBuilder.build(targetPathAbs, payloads = payloads, rootSegmentAlias = nodeBuilder.schema.rootSegment)
  }
  return when (targetNode) {
    is FlowNode<*> -> {
      val nextTargetPathAbs = targetPathAbs.append(targetNode.initial.path)
      targetNode.initial.payload?.also { payloads[nextTargetPathAbs] = it }
      maybeResolveInitial(
        targetNode.initial,
        nextTargetPathAbs,
        nodeBuilder,
        nodes,
        schema,
        payloads,
        callingRegionId,
        visitedPaths,
      )
    }

    is ParallelFlowNode<*> -> {
      val (parallelSchema, schemaPath) = findParentSchema(schema, targetPathAbs, inclusive = true)
      val resolved = mutableMapOf<RegionId, Path>()
      // Calling region stops at parallel node, but only when the parallel lives inside its subtree.
      // For a cross-region intermediate (targetPathAbs outside callingRegionId), writing here would
      // pollute the source region's alive list with a path it cannot consume.
      if (targetPathAbs.startsWith(callingRegionId.path)) {
        resolved[callingRegionId] = targetPathAbs
      }
      parallelSchema.regions.forEach { relativeRegionId ->
        val regionRootAbs = absoluteRegionRoot(schemaPath, relativeRegionId)
        val absoluteRegionId = RegionId(regionRootAbs)
        resolved.putAll(
          maybeResolveInitial(regionRootAbs, absoluteRegionId, nodeBuilder, nodes, schema, payloads, mutableSetOf()),
        )
      }
      resolved
    }

    is ScreenNode -> {
      mapOf(callingRegionId to targetPathAbs)
    }
  }
}

/**
 * Returns the region that owns [path] — the region whose root path is the longest prefix of [path] —
 * or [fallback] when no region's path is a prefix. Non-throwing counterpart of
 * [findOwningRegionIdOrThrow].
 */
internal fun owningRegionId(path: Path, regions: Collection<RegionId>, fallback: RegionId): RegionId =
  regions.filter { path.startsWith(it.path) }.maxByOrNull { it.path.length } ?: fallback

/** The node type at [path], or `null` if the schema can't resolve it. Defensive against traversal errors. */
private fun nodeTypeOrNull(schema: Schema, path: Path): Schema.NodeType? =
  runCatching { findNodeType(schema, path) }.getOrNull()

/** True when the node at [path] is a [Schema.NodeType.ParallelFlow] (false if the type can't be resolved). */
private fun isParallelFlowAt(schema: Schema, path: Path): Boolean =
  nodeTypeOrNull(schema, path) == Schema.NodeType.ParallelFlow

/**
 * Resolves a set of ABSOLUTE [candidates], each into the region that owns it. A candidate that passes
 * through a not-yet-initialized parallel is expanded via [initParallelAndRouteAbsolute] (re-materializing
 * the cold parallel and fanning its sibling regions out to their defaults); otherwise it descends its
 * own initial chain via [maybeResolveInitial]. Shared by the AbsoluteTarget arm and both HistoryTarget
 * (deep/shallow) arms of [resolveTransitionInRegion]'s NavigateTo handling.
 *
 * [initializedPaths] is updated in place so a later candidate does not re-initialize a region an earlier
 * one already produced. [alreadyChosenBase] holds paths chosen by earlier *targets* in the same
 * NavigateTo; this call's own running results are unioned on top so sibling regions of a cold parallel
 * are preserved across candidates. [defaultRegionId] owns a candidate when no materialized region does;
 * [fallbackNodes] is the build context when the owning region isn't materialized.
 */
private fun resolveAbsoluteLeaves(
  candidates: List<Path>,
  defaultRegionId: RegionId,
  fallbackNodes: Map<Path, Node>,
  allRegions: Map<RegionId, Region>,
  nodeBuilder: NodeBuilder,
  schema: Schema,
  payloads: MutableMap<Path, Any>,
  initializedPaths: MutableSet<Path>,
  alreadyChosenBase: Map<RegionId, Path>,
): Map<RegionId, Path> {
  val resolvedLeaves = mutableMapOf<RegionId, Path>()
  candidates.forEach { candidate ->
    val candidateRegionId = owningRegionId(candidate, allRegions.keys, fallback = defaultRegionId)
    val candidateNodes = allRegions[candidateRegionId]?.nodes ?: fallbackNodes
    val parallelOnPath = findParallelOnPath(candidate, candidateRegionId, initializedPaths, schema)
    val resolved = if (parallelOnPath != null) {
      initParallelAndRouteAbsolute(
        candidate, parallelOnPath, candidateRegionId, nodeBuilder, candidateNodes,
        schema, payloads, initializedPaths,
        alreadyChosen = alreadyChosenBase + resolvedLeaves,
      )
    } else {
      maybeResolveInitial(candidate, candidateRegionId, nodeBuilder, candidateNodes, schema, payloads)
    }
    resolved.keys.forEach { initializedPaths.add(it.path) }
    resolvedLeaves.putAll(resolved)
  }
  return resolvedLeaves
}

/**
 * Scans [absolutePath] for a [Schema.NodeType.ParallelFlow] node between [regionId].path (exclusive)
 * and [absolutePath] (exclusive) that is not yet represented in [initializedPaths].
 * Returns the first such path, or null if all intermediate nodes are already-alive regions.
 */
private fun findParallelOnPath(
  absolutePath: Path,
  regionId: RegionId,
  initializedPaths: Set<Path>,
  schema: Schema,
): Path? {
  for (len in (regionId.path.length + 1) until absolutePath.length) {
    val candidate = absolutePath.take(len)
    if (candidate in initializedPaths) continue
    if (isParallelFlowAt(schema, candidate)) {
      return candidate
    }
  }
  return null
}

/**
 * Walks every depth in `1 until regionRoot.length` and returns ancestor paths that are
 * (a) a [Schema.NodeType.ParallelFlow] AND
 * (b) the root of an imported sub-schema (distinguished from a regular parallel-in-flow by
 *     `findParentSchema(..., inclusive = true).path == candidate`).
 *
 * The returned list is ordered shallowest-first so callers can mount in parent-before-child
 * order. Errors from [findNodeType] are swallowed via `runCatching` to mirror the defensive
 * pattern in [findParallelOnPath] — a schema traversal failure on one candidate should not
 * prevent the rest from being considered.
 */
internal fun intermediateParallelAncestors(regionRoot: Path, schema: Schema): List<Path> {
  if (regionRoot.length <= 1) return emptyList()
  // getProperAncestors returns the proper ancestors nearest-first (deepest-first); asReversed()
  // yields the shallowest-first order this function contracts. The candidate set is identical to
  // the historical `for (depth in 1 until regionRoot.length)` walk (paths of length 1 .. len-1).
  return getProperAncestors(regionRoot, boundary = null).asReversed().filter { candidate ->
    if (!isParallelFlowAt(schema, candidate)) return@filter false
    val parent = runCatching { findParentSchema(schema, candidate, inclusive = true) }.getOrNull()
      ?: return@filter false
    parent.path == candidate
  }
}

/**
 * Initializes [parallelPath] and all its sub-regions, routing [absolutePath] into the
 * matching sub-region rather than its default initial screen. Handles nested uninitialized
 * parallels recursively via [initializedPaths].
 */
private fun initParallelAndRouteAbsolute(
  absolutePath: Path,
  parallelPath: Path,
  callingRegionId: RegionId,
  nodeBuilder: NodeBuilder,
  nodes: Map<Path, Node>,
  schema: Schema,
  payloads: MutableMap<Path, Any>,
  initializedPaths: Set<Path>,
  alreadyChosen: Map<RegionId, Path> = emptyMap(),
): Map<RegionId, Path> {
  val (parallelSchema, schemaPath) = findParentSchema(schema, parallelPath, inclusive = true)
  val resolved = mutableMapOf<RegionId, Path>()
  // Only assign to the calling region when the parallel lives inside its subtree. For a cross-region
  // intermediate (parallelPath outside callingRegionId), writing here would land a path the source
  // region cannot consume — calculateAliveNodes' `path.startsWith(regionId.path)` filter drops it,
  // potentially emptying the source region's _alive and crashing at `_active = _alive.last()`.
  if (parallelPath.startsWith(callingRegionId.path)) {
    resolved[callingRegionId] = parallelPath
  }
  parallelSchema.regions.forEach { relativeRegionId ->
    val regionRootAbs = absoluteRegionRoot(schemaPath, relativeRegionId)
    val absoluteRegionId = RegionId(regionRootAbs)
    if (absolutePath.startsWith(regionRootAbs)) {
      // This sub-region owns absolutePath — check for deeper nested uninitialized parallels
      val innerParallel = findParallelOnPath(absolutePath, absoluteRegionId, initializedPaths, schema)
      resolved.putAll(
        if (innerParallel != null) {
          initParallelAndRouteAbsolute(
            absolutePath, innerParallel, absoluteRegionId, nodeBuilder, nodes, schema, payloads, initializedPaths,
            alreadyChosen = alreadyChosen,
          )
        } else {
          maybeResolveInitial(absolutePath, absoluteRegionId, nodeBuilder, nodes, schema, payloads, mutableSetOf())
        },
      )
    } else {
      // Other sub-regions — preserve any active path a previous target in the same NavigateTo
      // chose for this region; otherwise initialize to the default initial state.
      if (absoluteRegionId in alreadyChosen) return@forEach
      resolved.putAll(
        maybeResolveInitial(regionRootAbs, absoluteRegionId, nodeBuilder, nodes, schema, payloads, mutableSetOf()),
      )
    }
  }
  return resolved
}

/** Longest-prefix owning region of [path] among [regions]; throws if none owns it. Throwing sibling of [owningRegionId]. */
private fun findOwningRegionIdOrThrow(regions: Collection<RegionId>, path: Path): RegionId =
  regions.sortedByDescending {
    it.path.length
  }.find { path.startsWith(it.path) }
    ?: error("failed to find regionId for path=\"${path}\", searched in ${regions.joinToString { it.path.toString() }}")

/**
 * Returns a parent flow path of a [path]. If node at [path] is already a [FlowNode], returns the [path] unmodified
 */
private fun findParentFlowPathInclusive(schema: Schema, path: Path): Path =
  if (findNodeType(schema, path) == Schema.NodeType.Flow) {
    path
  } else {
    path.toStepsReversed().firstOrNull {
      findNodeType(schema, it) == Schema.NodeType.Flow
    } ?: error(
      "no flow node found in path ancestry of \"$path\"; this is likely a schema configuration error",
    )
  }

/**
 * The (schema-relative) region within [schema] that owns [path]: the region whose absolute root
 * (see [absoluteRegionRoot], anchored at [schemaPath]) is the longest prefix of [path], falling back
 * to the first declared region. Matching goes through [absoluteRegionRoot] because local-flow
 * sub-regions carry segment ids from their own `.dot` file, so a raw `startsWith(regionId.path)`
 * would fail on the differing `@file` suffixes. Distinct from [owningRegionId], which matches against
 * already-materialized runtime regions rather than a schema's declared regions.
 */
private fun owningRegionInSchema(schema: Schema, schemaPath: Path, path: Path): RegionId = schema.regions
  .sortedByDescending { it.path.length }
  .firstOrNull { relRegionId -> path.startsWith(absoluteRegionRoot(schemaPath, relRegionId)) }
  ?: schema.regions.first()

internal fun findNodeType(rootSchema: Schema, path: Path): Schema.NodeType {
  val (activeSchema, schemaPath) = findParentSchema(rootSchema, path, inclusive = true)
  val relativePath = path.relativeToSchema(schemaPath)
  val regionId = owningRegionInSchema(activeSchema, schemaPath, path)
  return activeSchema.nodeType(regionId, relativePath, rootSegmentAlias = relativePath.firstSegment())
}

private fun Path.isRootInRegion(regionId: RegionId): Boolean = this == regionId.path

internal data class ResolvedTransition(
  val targetPaths: Map<RegionId, Path>,
  val payloads: Map<Path, Any>,
  val enqueuedEvents: List<Event>?,
) {

  companion object {
    val EMPTY = ResolvedTransition(emptyMap(), emptyMap(), null)
  }
}

/**
 * Merges two resolved transitions by unioning their target paths and payloads and concatenating
 * their enqueued events (normalizing an empty event list back to `null`). Used to fold each region's
 * / parallel's contribution into a single result.
 */
internal operator fun ResolvedTransition.plus(other: ResolvedTransition): ResolvedTransition = ResolvedTransition(
  targetPaths = targetPaths + other.targetPaths,
  payloads = payloads + other.payloads,
  enqueuedEvents = (enqueuedEvents.orEmpty() + other.enqueuedEvents.orEmpty()).takeIf { it.isNotEmpty() },
)
