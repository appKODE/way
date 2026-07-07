package ru.kode.way

/*
 * Canonical building blocks of the W3C SCXML "Algorithm for SCXML Interpretation" (Recommendation,
 * Appendix B), expressed over Way's absolute Path configuration. These formalize the
 * transition-scoping and exit-set computation that Way historically performed ad-hoc via prefix
 * decomposition (Path.toSteps) and longest-matching-region heuristics.
 *
 * Terminology map (SCXML -> Way):
 * - compound state / <state> with children -> Schema.NodeType.Flow (OR-state: exactly one child active)
 * - <parallel> -> Schema.NodeType.ParallelFlow (AND-state: all regions active simultaneously)
 * - atomic state -> Schema.NodeType.Screen
 * - the <scxml> root element -> the schema root (the single-segment Path)
 * - configuration (the set of currently-active states) -> the set of alive absolute Paths across all regions
 * - LCCA (Least Common Compound Ancestor) -> the nearest common ancestor that is a Schema.NodeType.Flow
 *   or the schema root
 *
 * These functions are intentionally decoupled from Schema: node-type lookup is supplied as a
 * `nodeTypeOf` function so they are pure over Path and unit-testable in isolation. At the call
 * site pass `{ p -> findNodeType(schema, p) }`.
 *
 * NOTE on the "down" direction: SCXML's `addDescendantStatesToEnter` (auto-entering the default
 * initial child of a compound state and every region of a parallel state) is node-coupled in
 * Way because FlowNode.initial is a runtime node property rather than static schema data -- that
 * direction is implemented by maybeResolveInitial in TargetResolution.kt. The functions here
 * cover the schema-static parts SCXML factors out: transition domain, LCCA, exit set, and the
 * ancestor "fill" between a target and the domain.
 */

/**
 * SCXML `getProperAncestors(state1, state2)`.
 *
 * Returns the proper ancestors of [path] — its parent, its parent's parent, and so on — ordered
 * **nearest-first** (parent before grandparent). A "proper" ancestor excludes [path] itself.
 *
 * When [boundary] is `null`, ancestors are returned all the way up to and including the schema root
 * (the single-segment path). When [boundary] is non-null it must be a proper ancestor of [path];
 * ancestors are returned **up to but not including** [boundary] (exactly SCXML's "up to but not
 * including state2").
 */
internal fun getProperAncestors(path: Path, boundary: Path?): List<Path> {
  val minLen = boundary?.let { it.length + 1 } ?: 1
  if (path.length - 1 < minLen) return emptyList()
  val result = ArrayList<Path>(path.length - minLen)
  for (len in (path.length - 1) downTo minLen) {
    result.add(path.take(len))
  }
  return result
}

/** `true` when [descendant] is a *proper* descendant of [ancestor] (strictly below it). */
internal fun isProperDescendant(descendant: Path, ancestor: Path): Boolean =
  descendant.length > ancestor.length && descendant.startsWith(ancestor)

/**
 * SCXML `isCompoundStateOrScxmlElement`: `true` when this path is a compound (OR-)state
 * ([Schema.NodeType.Flow]) or the schema root (the single-segment path). Such paths are the only
 * valid LCCAs and the only valid domains for a self-internal transition.
 */
private fun Path.isCompoundOrRoot(nodeTypeOf: (Path) -> Schema.NodeType): Boolean =
  length == 1 || nodeTypeOf(this) == Schema.NodeType.Flow

/**
 * SCXML `findLCCA(stateList)` — the Least Common Compound Ancestor of [paths].
 *
 * Walks the proper ancestors of the first path (nearest-first), and returns the first one that is
 * (a) a compound state ([Schema.NodeType.Flow]) or the schema root, AND (b) a proper ancestor of
 * every other path in [paths]. Parallel states are **not** valid LCCAs (matching SCXML's
 * `isCompoundStateOrScxmlElement` filter): a transition that would be scoped at a parallel is lifted
 * to the enclosing compound so the transition stays local to one region rather than tearing down
 * sibling regions.
 *
 * [paths] must be non-empty and share a common ancestor (they always do in a single schema — the
 * root). The schema root (single-segment path) always qualifies as a compound, so an LCCA always
 * exists.
 */
internal fun findLCCA(paths: List<Path>, nodeTypeOf: (Path) -> Schema.NodeType): Path {
  require(paths.isNotEmpty()) { "findLCCA requires at least one path" }
  val head = paths.first()
  val rest = paths.drop(1)
  for (anc in getProperAncestors(head, null)) {
    if (!anc.isCompoundOrRoot(nodeTypeOf)) continue
    if (rest.all { isProperDescendant(it, anc) }) return anc
  }
  error("no LCCA found for paths ${paths.map { it.toString() }}")
}

/**
 * SCXML `getTransitionDomain(t)` — the scope of a transition from [source] to [targets].
 *
 * - No targets → `null` (a targetless/internal transition that neither exits nor enters states).
 * - An [isInternal] transition whose [source] is compound ([Schema.NodeType.Flow] or the root) and
 *   whose every target is a proper descendant of [source] → [source] itself (the transition stays
 *   within the source without exiting/re-entering it).
 * - Otherwise → `findLCCA([source] + targets)`.
 *
 * The returned domain bounds both [computeExitSet] and the ancestor "fill" of the entry set, which
 * is what makes a deep transition **local**: states above the domain remain active and untouched.
 */
internal fun getTransitionDomain(
  source: Path,
  targets: List<Path>,
  isInternal: Boolean,
  nodeTypeOf: (Path) -> Schema.NodeType,
): Path? {
  if (targets.isEmpty()) return null
  if (isInternal &&
    source.isCompoundOrRoot(nodeTypeOf) &&
    targets.all { isProperDescendant(it, source) }
  ) {
    return source
  }
  return findLCCA(listOf(source) + targets, nodeTypeOf)
}

/**
 * SCXML `computeExitSet` (specialized to a single [domain]).
 *
 * Returns the members of [configuration] that are proper descendants of [domain] — i.e. the active
 * states the transition must exit — ordered **leaf-first** (deepest paths first). Leaf-first length
 * ordering is Way's existing exit order (`alive.reversed()`), the approximation of SCXML's reverse
 * document order that drives `onExit` from leaf to root.
 */
internal fun computeExitSet(domain: Path, configuration: Collection<Path>): List<Path> = configuration
  .filter { isProperDescendant(it, domain) }
  .sortedByDescending { it.length }

/**
 * The schema-static "ancestor fill" half of SCXML `computeEntrySet` — `addAncestorStatesToEnter`
 * restricted to the ancestors themselves.
 *
 * Returns every intermediate state that must be entered between [target] and its transition
 * [domain] (exclusive of both), ordered **root-first** (document/entry order). This is the
 * "recreate all the routes in-between" fill: navigating to a deep [target] re-enters each
 * compound/parallel ancestor down to it without the caller listing them.
 *
 * Sibling-region fill for any parallel ancestor (SCXML's recursion into a parallel's other regions)
 * is node-coupled (needs each region's default `initial`) and is handled by [maybeResolveInitial];
 * this function returns the on-path ancestors only.
 */
internal fun entryAncestors(target: Path, domain: Path): List<Path> = getProperAncestors(target, domain).asReversed()
