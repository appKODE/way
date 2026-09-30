package ru.kode.way

sealed interface Target {
  val path: Path
  val payload: Any?

  companion object
}

/**
 * Stands in for a `null` argument of a nullable node parameter in a payload map (whose values can't be null), so a
 * null argument can be told apart from a missing one. Generated node builders turn it back into `null`.
 */
object NullPayload

/**
 * A navigation target that resolves to a [ScreenNode].
 *
 * [path] is the relative segment path to the screen within its parent flow schema. The runtime
 * resolves it to an absolute path using the current navigation context.
 *
 * [payload] is an optional argument passed to the screen node when it is built.
 *
 * [ancestorPayloads] holds payloads of parameterized intermediate nodes on [path], keyed by that node's segment.
 * They are used only when such an ancestor is not alive and has to be rebuilt; an alive ancestor keeps its payload.
 * A `null` value is the argument of a nullable parameter and is stored as [NullPayload].
 */
data class ScreenTarget(
  override val path: Path,
  override val payload: Any? = null,
  val ancestorPayloads: Map<Segment, Any?> = emptyMap(),
) : Target

/**
 * A navigation target that resolves to a [FlowNode].
 *
 * [path] is the relative segment path to the flow within its parent schema. The runtime resolves
 * it to an absolute path and then follows the flow's [FlowNode.initial] chain to find the first
 * active [ScreenNode].
 *
 * [payload] is an optional argument passed to the flow node when it is built.
 *
 * [ancestorPayloads] holds payloads of parameterized intermediate nodes on [path], keyed by that node's segment.
 * They are used only when such an ancestor is not alive and has to be rebuilt; an alive ancestor keeps its payload.
 * A `null` value is the argument of a nullable parameter and is stored as [NullPayload].
 */
data class FlowTarget(
  override val path: Path,
  override val payload: Any? = null,
  val ancestorPayloads: Map<Segment, Any?> = emptyMap(),
) : Target

/**
 * A navigation target specified as a fully-qualified absolute path.
 *
 * Use this when you need to navigate to a node that is not reachable through relative resolution
 * from the current schema scope — for example, jumping across schema boundaries.
 *
 * [path] must be a complete path from the root segment of the navigation graph.
 *
 * [payloads] maps intermediate absolute paths (for parameterised flow or screen nodes) to their
 * constructor arguments — see the [payloads] property for the format and an example.
 */
data class AbsoluteTarget(
  /**
   * An absolute path to the target node
   */
  override val path: Path,
  /**
   * Mapping from the absolute path to payload for nodes with parameters along the path.
   * For example an AbsoluteTarget for target "app.login(x = 33).profile.permissions(y = 42).intro" this map needs to
   * contain entries:
   *
   * ```
   * app.login → 33
   * app.login.profile.permissions → 42
   * ```
   */
  val payloads: Map<Path, Any> = emptyMap(),
) : Target {
  override val payload: Any? = null
}

/**
 * A navigation target that restores the most-recently-active configuration of the flow/region at
 * [path] (an SCXML history pseudostate), instead of entering its default [FlowNode.initial].
 *
 * [path] is the fully-qualified absolute path of the flow (or region root) whose history to
 * restore.
 *
 * When [deep] is `false` (SCXML *shallow* history) the immediate child that was active when the
 * flow was last exited is restored, and that child then completes its own default initial
 * navigation normally. When [deep] is `true` (SCXML *deep* history) the full set of atomic
 * descendant(s) that were active at the last exit is restored directly.
 *
 * If the flow has never been exited before (no recorded history), navigation falls back to the
 * flow's default [FlowNode.initial], exactly as a [FlowTarget] to the same [path] would.
 *
 * [payload] is an optional argument passed to the flow node when it is (re)built.
 *
 * A restored parameterized node which is not alive is rebuilt with the argument it had when the flow was last exited:
 * the history record keeps these arguments and is replaced on the next exit of the flow. An alive node keeps its
 * current argument.
 */
data class HistoryTarget(override val path: Path, val deep: Boolean = false, override val payload: Any? = null) :
  Target

/**
 * Convenience constructor that assembles an [AbsoluteTarget] from a [rootSegment] and a chain of
 * [Target] hops. Each hop's [path] is appended sequentially to the running absolute path; its
 * [payload] (if any) is recorded at that absolute path in [AbsoluteTarget.payloads].
 *
 * Works correctly when every hop's [path] is a single segment (the common case for targets within
 * a single schema). For paths that cross multiple schema boundaries where sub-schema targets carry
 * multi-segment paths (i.e. the sub-schema target's path includes intermediate nodes), build
 * [AbsoluteTarget.path] and [AbsoluteTarget.payloads] explicitly using a named schema-root variable.
 *
 * Example — single schema, all single-segment hops:
 * ```kotlin
 * AbsoluteTarget(
 *     schema.rootSegment,
 *     Target.appFlow.login(userName = "Alice"),
 *     Target.appFlow.dashboard,
 * )
 * ```
 */
fun AbsoluteTarget(rootSegment: Segment, vararg hops: Target): AbsoluteTarget {
  var path = Path(rootSegment)
  val payloads = mutableMapOf<Path, Any>()
  for (hop in hops) {
    val hopRoot = path
    path = path.append(hop.path)
    hop.payload?.let { payloads[path] = it }
    val ancestorPayloads = when (hop) {
      is ScreenTarget -> hop.ancestorPayloads
      is FlowTarget -> hop.ancestorPayloads
      else -> emptyMap()
    }
    ancestorPayloads.forEach { (segment, value) ->
      val index = hop.path.segments.lastIndexOf(segment)
      check(index >= 0) { "ancestor payload segment \"${segment.id}\" is not on hop path \"${hop.path}\"" }
      payloads[hopRoot.append(Path(hop.path.segments.take(index + 1)))] = value ?: NullPayload
    }
  }
  return AbsoluteTarget(path, payloads)
}
