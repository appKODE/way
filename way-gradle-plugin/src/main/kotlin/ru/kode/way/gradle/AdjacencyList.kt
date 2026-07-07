package ru.kode.way.gradle

internal typealias AdjacencyList = Map<Node, List<Node>>

/**
 * Iterates only over [Node.Flow.Local] flow nodes, invoking [action] for each.
 *
 * [Node.Flow.LocalParallel] is deliberately excluded because parallel nodes require their own
 * specialised handling (they delegate to child flows via separate regions and do not participate
 * in the regular finish-event chain). Including them here would cause callers that generate
 * linear-flow artefacts (e.g. finish-event classes) to emit incorrect code for parallel nodes.
 *
 * Contrast with [mapFlow], which intentionally includes [Node.Flow.LocalParallel] so that a
 * NodeBuilder class is generated for every buildable flow type.
 */
internal inline fun AdjacencyList.forEachFlow(action: (Node.Flow, List<Node>) -> Unit) {
  this.forEach { (node, adjacent) ->
    if (node is Node.Flow.Local) {
      action(node, adjacent)
    }
  }
}

/**
 * Maps over all locally-owned flow nodes — both [Node.Flow.Local] and [Node.Flow.LocalParallel] —
 * returning a transformed list.
 *
 * [Node.Flow.LocalParallel] is included here because every parallel flow still needs its own
 * NodeBuilder class (the builder delegates to child-flow builders for each region). Excluding it
 * would leave parallel flows without a builder, breaking navigation entirely.
 *
 * Contrast with [forEachFlow], which intentionally excludes [Node.Flow.LocalParallel] because
 * callers of that function generate artefacts (e.g. finish-event sealed interfaces) that are only
 * meaningful for linear flows.
 *
 * [Node.Flow.Imported] (schema references) is excluded from both functions because imported flows
 * are defined — and their builders generated — in their own dot files.
 */
internal inline fun <T : Any> AdjacencyList.mapFlow(action: (Node.Flow, List<Node>) -> T): List<T> {
  val out = mutableListOf<T>()
  this.forEach { (node, adjacent) ->
    if (node is Node.Flow.Local || node is Node.Flow.LocalParallel) {
      out.add(action(node, adjacent))
    }
  }
  return out
}

internal fun AdjacencyList.findRootNode(): Node {
  keys.forEach {
    if (findParent(it) == null) return it
  }
  error("internal error: no root node in graph")
}

internal fun AdjacencyList.findParent(node: Node): Node? {
  entries.forEach { (n, adjacent) ->
    if (n == node) return@forEach // continue
    if (adjacent.contains(node)) {
      return n
    }
  }
  return null
}

/**
 * Finds all parents and returns them in the closest-to-farthest order, i.e. for
 * app -> screen1 -> screen2 -> screen3
 *
 * findAllParents(screen3) => [screen2, screen1, app]
 */
internal fun AdjacencyList.findAllParents(node: Node, includeThis: Boolean = false): List<Node> {
  val parents = ArrayList<Node>(this.size)
  if (includeThis) {
    parents.add(node)
  }
  var next: Node? = findParent(node)
  while (next != null) {
    parents.add(next)
    next = findParent(next)
  }
  return parents
}

internal fun AdjacencyList.findParentFlow(node: Node): Node? {
  var next: Node? = findParent(node)
  while (next != null) {
    if (next is Node.Flow) return next
    next = findParent(next)
  }
  return null
}

/**
 * True when [node] is a [Node.Flow.Local] whose immediate parent is a [Node.Flow.LocalParallel] —
 * i.e. a LOCAL sub-region flow that owns its own virtual sub-schema.
 */
internal fun AdjacencyList.isLocalChildOfParallel(node: Node): Boolean =
  node is Node.Flow.Local && findParent(node) is Node.Flow.LocalParallel

/** The [Node.Flow.LocalParallel] that is [node]'s immediate parent, or null when the parent isn't a parallel. */
internal fun AdjacencyList.parallelParentOf(node: Node): Node.Flow.LocalParallel? =
  findParent(node) as? Node.Flow.LocalParallel

/**
 * Returns the "region roots" — the entry-point flow of every independently-navigable region in this schema.
 *
 * A region root is a direct child of any [Node.Flow.LocalParallel] whose immediate parent is NOT itself a
 * LocalParallel. For a plain (non-parallel) graph with no such parallel, the single root flow node is the only
 * region root.
 *
 * This intentionally collects region roots at EVERY nesting depth, not just the top parallel's children. A
 * parallel reached through a linear [Node.Flow.Local] flow (e.g.
 * `acmeAppFlow[parallelFlow] -> acmeMainFlow[flow] -> acmeTabsFlow[parallelFlow] -> acmeHomeTab/acmeExploreTab`)
 * starts a NEW region tier, so `acmeHomeTab`/`acmeExploreTab` surface as region roots alongside the top parallel's
 * own children `acmeMainFlow`/`acmeAuthFlow` — four flat region roots in total. This is CORRECT and required, NOT
 * over-collection: the runtime region model is FLAT. `NavigationService.materializeRegion` iterates
 * `schema.regions` once and creates one top-level `Region` per entry, keyed by its full absolute path (region
 * depth is encoded in the path, never in map nesting), and `pruneOrphanRegions` pins every `schema.regions` entry
 * for the service lifetime. The generated `AcmeAppFlowSchema.regions` must therefore list all four so the runtime
 * materialises all four. This is asserted directly by the runtime contract test
 * "acme-style layout: each region has its own absolute regionId.path anchored at acmeAppFlow"
 * (`way/src/commonTest/.../ParallelNodeTest.kt`); reducing this
 * to two would fail that test and break navigation. The same flat set is what `AdjacencyListTest` pins.
 *
 * The one exclusion — a parallel whose immediate parent IS another parallel (parallel-in-parallel, e.g.
 * `main[parallelFlow] -> one[parallelFlow] -> alpha/beta`) — is deliberate and consistent with the flat model:
 * `one` is itself a region root, so its children `alpha`/`beta` are that parallel's own sub-regions, materialised
 * within `one`'s virtual sub-schema rather than as top-level regions of `main`. Surfacing them here would
 * double-count. (Contrast: a parallel under a LOCAL flow is not itself a region root, so ITS children ARE
 * promoted — that is the `acmeTabsFlow` case above.)
 *
 * Note on the finish-event coupling: because these region roots also drive nested-parallel finish-event
 * discovery, the top schema surfacing `acmeHomeTab`/`acmeExploreTab` is what makes
 * [buildChildFinishEventFileSpecs] emit the nested parallel's `AcmeTabsFlowChildFinishRequest` interface. That
 * coupling is a consequence of the flat model, not a defect — see the `createChildFlowFinishRequestEvent` /
 * `buildChildFinishEventFileSpecs` emission paths.
 */
internal fun buildRegionRoots(adjacencyList: AdjacencyList): List<Node> {
  val regionRoots = mutableListOf<Node>()
  adjacencyList.forEach { (node, children) ->
    if (node is Node.Flow.LocalParallel && adjacencyList.findParent(node) !is Node.Flow.LocalParallel) {
      regionRoots.addAll(children)
    }
  }
  if (regionRoots.isEmpty()) {
    regionRoots.add(adjacencyList.findRootNode())
  }
  return regionRoots
}

internal fun dfs(adjacencyList: AdjacencyList, root: Node, action: (Node) -> Unit) =
  dfsWhile(adjacencyList, root) { node ->
    action(node)
    true // always descend
  }

/**
 * Returns every non-root [Node.Flow.LocalParallel] in the graph — i.e. every parallel that needs
 * its own virtual Schema class rather than being folded into the outer schema.
 *
 * Includes both:
 * - parallels whose parent is another [Node.Flow.LocalParallel] (parallel-in-parallel)
 * - parallels whose parent is a [Node.Flow.Local] (parallel-as-child-of-flow)
 *
 * The only LocalParallel excluded is the root of the file's own graph (no parent), which IS the
 * outer schema and therefore doesn't need a virtual sub-schema.
 */
internal fun AdjacencyList.nestedLocalParallels(): List<Node.Flow.LocalParallel> =
  keys.filterIsInstance<Node.Flow.LocalParallel>()
    .filter { findParent(it) != null }

/**
 * Returns every [Node.Flow.Local] whose immediate parent is a [Node.Flow.LocalParallel].
 *
 * These are LOCAL sub-region flows (e.g. `mainFlow` inside `appFlow [type=parallelFlow]`,
 * or `par05Alpha` inside `par05Main [type=parallelFlow]`). Each one needs its own virtual
 * sub-schema so that its NodeBuilder operates on a schema whose `rootSegment` IS the LOCAL
 * flow itself — making the `rootSegmentAlias` contract behave the same for LOCAL sub-regions
 * as it does for IMPORTED sub-regions and nested parallels.
 *
 * Without this, descendant path lookups inside the LOCAL flow's NodeBuilder would double the
 * LOCAL flow's segment (e.g. `Path(par05Alpha, par05Alpha, par05AlphaScreen1)`), because the
 * parent passes `alias = par05Alpha` to a child that shares the parent's schema.
 */
internal fun AdjacencyList.localChildrenOfLocalParallel(): List<Node.Flow.Local> {
  val result = mutableListOf<Node.Flow.Local>()
  this.forEach { (node, children) ->
    if (node is Node.Flow.LocalParallel) {
      children.forEach { child ->
        if (child is Node.Flow.Local) {
          result.add(child)
        }
      }
    }
  }
  return result
}

/**
 * Returns every node in this graph that owns its own virtual sub-schema:
 * - non-root [Node.Flow.LocalParallel] (nested parallels)
 * - [Node.Flow.Local] whose immediate parent is a [Node.Flow.LocalParallel]
 *
 * Used to drive (a) virtual-schema generation in [buildSpecs] and (b) ownership lookups in
 * [NodeBuilderCodegen]; flows that walk up to one of these roots use that root's virtual
 * sub-schema rather than the outer file's schema.
 */
internal fun AdjacencyList.virtualSubSchemaRoots(): List<Node> = nestedLocalParallels() + localChildrenOfLocalParallel()

/**
 * Extracts the sub-graph rooted at [root] (all nodes reachable from [root]).
 * Useful for generating a virtual Schema for a nested [Node.Flow.LocalParallel].
 */
internal fun AdjacencyList.subgraphFor(root: Node): AdjacencyList {
  val result = LinkedHashMap<Node, List<Node>>(size)
  dfs(this, root) { node ->
    result[node] = this[node].orEmpty()
  }
  return result
}

/**
 * DFS variant where [action] returns `true` to descend into a node's children, `false` to skip.
 * Use this instead of [dfs] when you need to prune branches (e.g. stop at nested LocalParallels).
 */
internal fun dfsWhile(adjacencyList: AdjacencyList, root: Node, action: (Node) -> Boolean) {
  val discovered = HashSet<Node>(adjacencyList.size)
  val stack = ArrayDeque<Node>(adjacencyList.size)
  stack.add(root)
  while (stack.isNotEmpty()) {
    val v = stack.removeLast()
    if (!discovered.contains(v)) {
      val descend = action(v)
      discovered.add(v)
      if (descend) {
        stack.addAll(adjacencyList[v].orEmpty())
      }
    }
  }
}
