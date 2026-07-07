package ru.kode.way.compose

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import ru.kode.way.FlowTransition
import ru.kode.way.NavigationService
import ru.kode.way.NavigationState
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.ParallelFlowNode
import ru.kode.way.Path
import ru.kode.way.Region
import ru.kode.way.RegionId
import ru.kode.way.append
import ru.kode.way.drop
import ru.kode.way.startsWith

@ExperimentalAnimationApi
@Composable
fun NodeHost(
  service: NavigationService<*>,
  transitionSpec: AnimatedContentTransitionScope<Path?>.() -> ContentTransform = defaultTransitionSpec,
) {
  LaunchedEffect(service) {
    if (!service.isStarted()) {
      service.start()
    }
  }
  // For parallel-flow-rooted schemas the top-level node is the parallel-flow itself; render its
  // Content() directly so its `NodeHost(regionId)` calls per sub-region drive the layout. Falls
  // through to the legacy "render the smallest-path region's active node" path otherwise.
  //
  // A single SaveableStateHolder is allocated unconditionally and shared by both branches, and the
  // branch is selected with a plain if/else (no early `return`). This keeps the slot table stable
  // when `collectRootNode` flips from its `null` initial value to the resolved root on the first
  // committed transition — two separate holders reached via early return would tear down and remount
  // the frame-1 subtree on that flip, discarding transient/saveable state.
  val rootNodeWithPath by collectRootNode(service)
  val saveableStateHolder = rememberSaveableStateHolder()
  CompositionLocalProvider(LocalNavigationService provides service) {
    val root = rootNodeWithPath
    val rootNode = root?.node
    if (root != null && rootNode is ComposableNode) {
      ComposableNodeContent(rootNode, root.path, Modifier, saveableStateHolder)
    } else {
      FirstRegionFallback(service, saveableStateHolder, transitionSpec, root)
    }
  }
}

/**
 * Fallback render path for a flow-rooted schema (or a parallel root that isn't a [ComposableNode]):
 * renders the FIRST declared region's active node in an [AnimatedContent]. When a non-ComposableNode
 * parallel [root] is present, logs the degradation so it isn't silently rendered as first-region only.
 */
@ExperimentalAnimationApi
@Composable
private fun FirstRegionFallback(
  service: NavigationService<*>,
  saveableStateHolder: SaveableStateHolder,
  transitionSpec: AnimatedContentTransitionScope<Path?>.() -> ContentTransform,
  root: NodeWithPath?,
) {
  if (root != null) {
    Log.w(
      LOG_TAG,
      "parallel-flow root \"${root.path}\" is not a ComposableNode; only the first region will render — " +
        "implement ComposableNode on it and call NodeHost(regionId) per sub-region in its Content()",
    )
  }
  val activeNode by collectActiveNode(service)
  NodeAnimatedContent(
    activeNode = activeNode,
    contentModifier = Modifier,
    saveableStateHolder = saveableStateHolder,
    transitionSpec = transitionSpec,
    label = "NodeHost",
  )
}

/** Renders [node]'s [ComposableNode.Content] under a [SaveableStateHolder] entry keyed by [path], with [path] exposed via [LocalNodePath]. */
@Composable
private fun ComposableNodeContent(
  node: ComposableNode,
  path: Path,
  modifier: Modifier,
  saveableStateHolder: SaveableStateHolder,
) {
  saveableStateHolder.SaveableStateProvider(path.toSaveableKey()) {
    CompositionLocalProvider(LocalNodePath provides path) {
      node.Content(modifier)
    }
  }
}

private const val LOG_TAG = "way-compose"

/**
 * Renders [activeNode] with an [AnimatedContent], releasing every node that leaves.
 *
 * The [AnimatedContent] target is the node's [Path] — a lightweight value — NOT the [NodeWithPath]
 * itself. This is deliberate and load-bearing for memory: a Compose `Transition` retains its
 * previous state in `segment.initialState` until the next transition starts, and its
 * currently-visible list keeps outgoing states during the exit animation. Keying on [NodeWithPath]
 * would therefore strand the whole graph of an already-exited [Node] (its DI scope, view models,
 * child nodes) in the composition's SlotTable for the entire lifetime of this host — the exact leak
 * this indirection avoids. Nodes are resolved for rendering from [nodeCache] instead.
 *
 * When a keyed content permanently leaves the [AnimatedContent] (its exit transition has finished)
 * and the node is no longer active, its cached [Node] and its [SaveableStateHolder] entry are
 * purged, making the exited node weakly reachable so it can be collected.
 */
@ExperimentalAnimationApi
@Composable
private fun NodeAnimatedContent(
  activeNode: NodeWithPath?,
  contentModifier: Modifier,
  saveableStateHolder: SaveableStateHolder,
  transitionSpec: AnimatedContentTransitionScope<Path?>.() -> ContentTransform,
  label: String,
) {
  // key -> Node, for the states AnimatedContent may still render (the active node plus any node
  // still animating out). Snapshot-backed so a write is observed by the content lambda's read.
  val nodeCache = remember { mutableStateMapOf<String, Node>() }
  // Keys currently mounted by AnimatedContent — added on enter, removed once their content (the
  // exit animation included) leaves. Snapshot-backed so the cleanup effect re-runs when it changes.
  val mountedKeys = remember { mutableStateListOf<String>() }
  // Keys we have handed to the SaveableStateHolder, so the cleanup effect knows what to purge. Plain
  // (non-snapshot) set: only read imperatively inside the effect, never during composition.
  val trackedKeys = remember { mutableSetOf<String>() }

  val activePath = activeNode?.path
  val activeKey = activePath?.toSaveableKey()
  if (activeNode != null && activeKey != null && nodeCache[activeKey] !== activeNode.node) {
    // Guard the write so an unchanged node does not record a redundant snapshot mutation each frame.
    nodeCache[activeKey] = activeNode.node
  }

  AnimatedContent(
    transitionSpec = transitionSpec,
    targetState = activePath,
    contentKey = { it?.toSaveableKey() },
    label = label,
  ) { path ->
    val key = path?.toSaveableKey()
    val node = key?.let { nodeCache[it] }
    if (key != null && path != null && node is ComposableNode) {
      DisposableEffect(key) {
        trackedKeys.add(key)
        mountedKeys.add(key)
        onDispose {
          mountedKeys.remove(key)
          nodeCache.remove(key)
        }
      }
      ComposableNodeContent(node, path, contentModifier, saveableStateHolder)
    } else {
      if (path != null) {
        if (node is ParallelFlowNode<*>) {
          Log.w(
            LOG_TAG,
            "parallel-flow node at \"$path\" is not a ComposableNode; rendering an empty content — " +
              "implement ComposableNode on it and call NodeHost(regionId) per sub-region in its Content()",
          )
        } else {
          Log.d(LOG_TAG, "didn't find a ComposableNode for \"$path\", rendering an empty content")
        }
      }
      Box {}
    }
  }

  // Purge SaveableStateHolder state for keys whose content has fully left AnimatedContent and are no
  // longer active (the cached Node itself is already dropped promptly in onDispose above; this also
  // acts as a backstop for it). Done OUTSIDE the SaveableStateProvider content so removeState runs
  // AFTER SaveableStateProvider's own dispose-time saveState(): a removeState placed inside the
  // provider content would be undone, because sibling effects dispose child-before-parent and
  // saveState() would immediately re-add the entry.
  LaunchedEffect(mountedKeys.toList(), activeKey) {
    trackedKeys.toList()
      .filter { it != activeKey && it !in mountedKeys }
      .forEach { key ->
        saveableStateHolder.removeState(key)
        nodeCache.remove(key)
        trackedKeys.remove(key)
      }
  }
}

/**
 * Injective SaveableStateHolder key for a node path. Uses the full [ru.kode.way.Segment.id] of each
 * segment — NOT [Path.toString], which joins `Segment.name` and strips the `@graphId:file`
 * disambiguator, collapsing two distinct cross-module paths to the same key and bleeding restored
 * `rememberSaveable` state from one node into another.
 */
private fun Path.toSaveableKey(): String = segments.joinToString(".") { it.id }

/**
 * Returns the [NavigationState.rootNode] paired with its absolute path, observed reactively.
 * Non-null only when the schema's root is a [ru.kode.way.ParallelFlowNode]; flow-rooted schemas
 * stay at `null` (callers fall through to [collectActiveNode]).
 */
@Composable
private fun collectRootNode(service: NavigationService<*>): State<NodeWithPath?> =
  service.produceTransitionState<NodeWithPath?>(initial = null) { s ->
    val node = s.rootNode
    val path = s.rootNodePath
    if (node != null && path != null) NodeWithPath(path, node) else null
  }

/**
 * A [State] reflecting [transform] applied to every [NavigationState] this service emits (and
 * `initial` before the first). Registers a transition listener for the composition's lifetime and
 * removes it on dispose — the listener lifecycle every collector in this module shares. Extra [keys]
 * (beyond the service) re-key the underlying [produceState].
 */
@Composable
internal fun <T> NavigationService<*>.produceTransitionState(
  initial: T,
  vararg keys: Any?,
  transform: (NavigationState) -> T,
): State<T> = produceState(initial, this, *keys) {
  val listener = { s: NavigationState -> value = transform(s) }
  addTransitionListener(listener)
  awaitDispose { removeTransitionListener(listener) }
}

private fun Region.toNodeWithPath(): NodeWithPath = NodeWithPath(active, activeNode)

@ExperimentalAnimationApi
@Composable
fun <R : Any> NodeHost(nodeBuilder: NodeBuilder, onFinishRequest: (R) -> FlowTransition<Unit>) {
  // Read the latest onFinishRequest without recreating the service: the service is keyed on
  // nodeBuilder only, so a new lambda passed on recomposition (the common case for an inline
  // lambda) would otherwise be ignored and the stale callback kept forever.
  val currentOnFinishRequest by rememberUpdatedState(onFinishRequest)
  val service = remember(nodeBuilder) {
    NavigationService(nodeBuilder) { result: R -> currentOnFinishRequest(result) }
  }
  // This overload OWNS the service it creates, so it must release it: cleanDispose() fires
  // onDispose() leaf-to-root on all alive nodes (freeing their DI/coroutine scopes) and clears all
  // listeners. Runs when NodeHost leaves composition OR when nodeBuilder changes (the old service is
  // disposed before remember installs the new one), preventing a node + listener leak.
  DisposableEffect(service) {
    onDispose { service.cleanDispose() }
  }
  NodeHost(service)
}

@ExperimentalAnimationApi
val defaultTransitionSpec: AnimatedContentTransitionScope<Path?>.() -> ContentTransform = {
  val initial = initialState
  val target = targetState
  when {
    initial != null && target != null -> {
      // A very basic attempt at distinguishing push from pop: check if new target is contained in the
      // old one -> going back
      // This doesn't work sufficiently well though and should be improved
      // (see sample-compose, it has weird transitions)
      // TODO Calculate transitions based on more clever heuristics. They can require looking into Schema to
      //   determine least common parent Flow of two paths and also can query actual alive Nodes for hints on
      //   transitions they desire in ambiguous situations
      if (!(initial.length > target.length && initial.startsWith(target))) {
        slideIntoContainer(SlideDirection.Left) togetherWith slideOutOfContainer(SlideDirection.Left)
      } else {
        slideIntoContainer(SlideDirection.Right) togetherWith slideOutOfContainer(SlideDirection.Right)
      }
    }

    // these defaults are taken from AnimatedContent's sources
    else -> (fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith fadeOut(animationSpec = tween(90)))
      .using(sizeTransform = null)
  }
}

/**
 * When the schema has multiple flow regions, this returns the first one declared.
 * For deterministic rendering of every region use the [NodeHost] overload that accepts a [RegionId].
 */
@Composable
fun collectActiveNode(service: NavigationService<*>): State<NodeWithPath?> =
  service.produceTransitionState<NodeWithPath?>(initial = null) { s ->
    s.regions.entries.firstOrNull()?.value?.toNodeWithPath()
  }

@Composable
fun collectActiveNode(service: NavigationService<*>, regionId: RegionId): State<NodeWithPath?> =
  service.produceTransitionState<NodeWithPath?>(initial = null, keys = arrayOf(regionId)) { s ->
    s.regions[regionId]?.toNodeWithPath()
  }

/**
 * Renders the active screen in [regionId].
 *
 * Call from a parallel node's [ComposableNode.Content] body to render each sub-region's screen stack.
 *
 * Requires BOTH [LocalNavigationService] AND [LocalNodePath] to be provided by a parent
 * [NodeHost]. The parent [NodeHost] (the one that takes a [NavigationService]) installs both
 * composition locals automatically: it provides the service via [LocalNavigationService] and the
 * absolute path of the rendered node via [LocalNodePath]. Without that wrapping parent,
 * `LocalNavigationService.current` will throw with a clear error.
 *
 * When [regionId] is schema-relative (i.e. its path does not already start with the parent path),
 * it is resolved to the absolute [RegionId] used as the key in [NavigationState.regions], mirroring
 * the runtime's `absoluteRegionRoot` logic. As a special case, length-1 relative regionIds
 * (e.g. an imported non-parallel schema) cannot supply a tail to append; the guard at the
 * `regionId.path.length > 1` check uses the parent path itself as the absolute path, mirroring
 * `TargetResolution.absoluteRegionRoot` — without it, `Path.drop(1)` would return an empty Path
 * and the init `check(segments.isNotEmpty())` would throw on first composition.
 */
@ExperimentalAnimationApi
@Composable
fun NodeHost(
  regionId: RegionId,
  modifier: Modifier = Modifier,
  transitionSpec: AnimatedContentTransitionScope<Path?>.() -> ContentTransform = defaultTransitionSpec,
) {
  val service = LocalNavigationService.current
  val parentPath = LocalNodePath.current
  // If we're rendered inside a parallel node's Content (parentPath != null) and the supplied
  // regionId is schema-relative (i.e. its path does not already start with parentPath),
  // resolve it to the absolute regionId used as the key in NavigationState.regions.
  // This mirrors the runtime's absoluteRegionRoot logic.
  val absoluteRegionId = remember(parentPath, regionId) {
    if (parentPath != null && !regionId.path.startsWith(parentPath)) {
      // Length-1 relative regionIds (e.g. an imported non-parallel schema) cannot supply a
      // tail to append; in that case the parent path itself is the absolute path. Mirrors the
      // guard in TargetResolution.absoluteRegionRoot — without it, Path.drop(1) returns an
      // empty Path and the init `check(segments.isNotEmpty())` throws on first composition.
      val tail = if (regionId.path.length > 1) regionId.path.drop(1) else null
      RegionId(if (tail != null) parentPath.append(tail) else parentPath)
    } else {
      regionId
    }
  }
  val activeNode by collectActiveNode(service, absoluteRegionId)
  val saveableStateHolder = rememberSaveableStateHolder()
  NodeAnimatedContent(
    activeNode = activeNode,
    contentModifier = modifier,
    saveableStateHolder = saveableStateHolder,
    transitionSpec = transitionSpec,
    label = "NodeHost-${absoluteRegionId.path}",
  )
}

@Immutable
data class NodeWithPath(val path: Path, val node: Node)
