package ru.kode.way

class NavigationState internal constructor(
  internal val _regions: MutableMap<RegionId, Region>,
  internal val _nodeExtensionPoints: MutableList<NodeExtensionPoint>,
  internal val _enqueuedEvents: ArrayDeque<Event>,
  /**
   * Absolute-path-keyed payloads accumulated across transitions. A `NavigateTo` carries
   * payloads for the path it targets; without this store the runtime would forget them as
   * soon as the next transition ran, and any subsequent rebuild of an already-once-parameterized
   * flow (lazy `*NodeBuilder` factory caches get invalidated when the active path moves away
   * and back) would crash with `no payload for "<path>"`. Pruned in lockstep with each region's
   * alive set during `synchronizeNodes`.
   */
  internal val _payloads: MutableMap<Path, Any> = mutableMapOf(),
  /**
   * Intermediate [ParallelFlowNode] roots discovered while materialising a schema whose sub-region
   * is itself a parallel-rooted schema (parallel-rooted nested inside parallel-rooted). These nodes
   * live ABOVE the runtime regions — they were entered during InitEvent so their `onEntry` fires
   * and Compose can render them, but they do not own a region of their own and so are not visible
   * through [regions]. Tracked here so [NavigationService.cleanDispose] can fire `onDispose`
   * on them when the service shuts down AND so [resolveTransition] can dispatch events through
   * each intermediate parallel's `transition()` (a ChildFinishRequest bubbled up from a leaf
   * region must reach the nearest enclosing parallel, not only the outermost root parallel).
   */
  internal val _intermediateParallels: MutableMap<Path, IntermediateParallel> = mutableMapOf(),
  /**
   * SCXML history store, keyed by the absolute path of a compound flow/region → the atomic leaf
   * path(s) that were active under it just before it was last exited. Recorded in
   * [NavigationService] when a flow subtree leaves the alive set (see `recordHistoryOnExit`) and
   * consulted when a [HistoryTarget] is resolved. A single leaf is enough to satisfy both shallow
   * (take the immediate child of the flow, then run its own default initial) and deep (restore the
   * recorded leaf directly) restores. Rolled back with every other slot on a thrown transition via
   * [NavigationService]'s transaction snapshot.
   */
  internal val _history: MutableMap<Path, List<Path>> = mutableMapOf(),
) {
  val regions: Map<RegionId, Region> = _regions
  val payloads: Map<Path, Any> = _payloads

  /**
   * Top-level [ParallelFlowNode] when the schema's root is a parallel-flow; `null` for the
   * common flow-rooted schema case.
   *
   * Set during [InitEvent] processing in [NavigationService]. Compose's `NodeHost(service)`
   * reads this to render the parallel-flow's `Content()` at the top instead of falling back to
   * the first region's active node.
   */
  var rootNode: Node? = null
    internal set

  /** Absolute path of [rootNode], or `null` when [rootNode] is `null`. */
  var rootNodePath: Path? = null
    internal set

  /**
   * Finish-transition builder for a parallel-flow root. When the root parallel-flow returns
   * `Finish(R)` from its `transition`, the runtime invokes this to convert the result into a
   * transition that reaches the NavigationService's `onFinishRequest`. `null` for flow-rooted
   * schemas (the equivalent slot lives on each [Region]'s `_rootFinishTransitionBuilder`).
   */
  @Suppress("PropertyName")
  internal var _rootFinishTransitionBuilder: ((Any) -> Transition)? = null

  fun regionByName(name: String): Region? = _regions.entries.find { it.key.path.lastSegment().name == name }?.value

  override fun toString(): String = "NavigationState(_regions=$_regions)"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    other as NavigationState

    if (_regions != other._regions) return false

    return true
  }

  override fun hashCode(): Int = _regions.hashCode()

  // TODO @RemoveMutable remove if switch away from mutable collections happens
  internal fun copy(): NavigationState = NavigationState(
    _regions = this.regions.mapValuesTo(mutableMapOf()) { it.value.copy() },
    _nodeExtensionPoints = this._nodeExtensionPoints.toMutableList(),
    _enqueuedEvents = ArrayDeque(this._enqueuedEvents),
    _payloads = this._payloads.toMutableMap(),
    _intermediateParallels = this._intermediateParallels.toMutableMap(),
    _history = this._history.toMutableMap(),
  ).also {
    it.rootNode = this.rootNode
    it.rootNodePath = this.rootNodePath
    it._rootFinishTransitionBuilder = this._rootFinishTransitionBuilder
  }
}

/**
 * Pair of an intermediate [ParallelFlowNode] root and the finish-transition builder used to
 * convert a `Finish(result)` returned from its `transition()` into a transition that reaches the
 * nearest enclosing parallel-flow (via a `ChildFinishRequest` event) or, for a single-segment
 * root path, the [NavigationService]'s `onFinishRequest`. Mirrors the per-region
 * `_rootFinishTransitionBuilder` on [Region] for parallels that do not own a region of their own.
 *
 * [initMounted] distinguishes the two structural classes of intermediate parallels:
 * - `true`: mounted during the InitEvent walk (parallel-rooted schema imported as a sub-region of
 *   another parallel-rooted schema). No flow region above it owns its path in any `alive` list,
 *   so the runtime cannot use parent-region reachability to decide unmount. These stay registered
 *   for the lifetime of the schema's region tree and are torn down only via [cleanDispose].
 * - `false`: mounted lazily during a runtime NavigateTo into a previously-unmaterialized
 *   parallel-rooted sub-region inside an enclosing flow region. The enclosing flow region's `alive`
 *   list pins the intermediate path (placed there by `initParallelAndRouteAbsolute`). When the
 *   enclosing region's `alive` no longer contains the intermediate path, the intermediate is
 *   orphaned and must be unmounted (its `onExit` fires and its descendant sub-regions get pruned).
 */
internal data class IntermediateParallel(
  val node: ParallelFlowNode<*>,
  val finishBuilder: (Any) -> Transition,
  val initMounted: Boolean,
)

class Region internal constructor(
  internal val _nodes: MutableMap<Path, Node>,
  internal var _active: Path,
  internal var _alive: MutableList<Path>,
  internal val _rootFinishTransitionBuilder: (Any) -> Transition,
) {
  val nodes: Map<Path, Node> = _nodes
  val active: Path get() = _active
  val activeNode get() = nodes[active] ?: error("internal error: no node at path $active")

  // TODO rename active -> attached/top/current, alive -> active?
  val alive: List<Path> get() = _alive

  // Structural copy: new map/list instances with the same Path keys and Node references.
  // Node instances are SHARED between original and copy; mutations to Node state affect both.
  // TODO @RemoveMutable remove if switch away from mutable collections happens
  internal fun copy(): Region = Region(
    _nodes = this._nodes.toMutableMap(),
    _active = this._active,
    _alive = this._alive.toMutableList(),
    _rootFinishTransitionBuilder = this._rootFinishTransitionBuilder,
  )

  override fun toString(): String = "Region(_nodes=$_nodes, _active=$_active)"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false
    other as Region
    if (_active != other._active) return false
    if (_alive != other._alive) return false
    if (_nodes.keys != other._nodes.keys) return false
    // Node instances are intentionally excluded from equals/hashCode: the listener dispatch loop
    // uses state.copy() which shares Node references between copies; equality must remain stable
    // if synchronizeNodes replaces a Node instance at an already-present path.
    return true
  }

  override fun hashCode(): Int {
    var result = _active.hashCode()
    result = 31 * result + _alive.hashCode()
    result = 31 * result + _nodes.keys.hashCode()
    // Node instances excluded from hashCode for the same reason as equals — see equals() above.
    return result
  }
}
