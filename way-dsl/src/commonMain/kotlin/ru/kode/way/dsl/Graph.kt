package ru.kode.way.dsl

import ru.kode.way.Event
import ru.kode.way.FlowTarget
import ru.kode.way.HistoryTarget
import ru.kode.way.NodeBuilder
import ru.kode.way.NullPayload
import ru.kode.way.Path
import ru.kode.way.RegionId
import ru.kode.way.Schema
import ru.kode.way.ScreenTarget
import ru.kode.way.Segment
import ru.kode.way.append

/**
 * A navigation graph declared in Kotlin, the counterpart of a `.dot` file which needs no Gradle plugin. Nodes are
 * properties of a subclass, edges are declared with [to] in its `init` block:
 *
 * ```kotlin
 * object LoginGraph : Graph("Login") {
 *   val login = parameterizedFlow<String, LoginResult>("login")
 *   val credentials = screen("credentials")
 *   val otp = parameterizedScreen<String>("otp")
 *
 *   init {
 *     login to credentials to otp
 *   }
 * }
 * ```
 *
 * The node without a parent is the root, it is a flow or a parallel flow. The rules are the ones of a `.dot` file:
 * - the children of a parallel flow (its regions) are flows, parallel flows or [include]d graphs;
 * - a [history] node is a leaf under a flow, or under a parallel flow which has a flow above it;
 * - only an [include]d graph may have several parents, which are not parallel flows and are in the same region.
 *
 * Nodes are identified by `id@graphId`, so [id] has to be unique among the graphs of a
 * [ru.kode.way.NavigationService]. The graph is checked and frozen on the first access to [schema] or [nodeBuilder].
 */
abstract class Graph(val id: String) {
  private val nodes = LinkedHashMap<String, NodeRef>()
  private val parents = HashMap<NodeRef, MutableList<NodeRef>>()
  private val children = HashMap<NodeRef, MutableList<NodeRef>>()
  private var frozen = false

  /** The [Schema] of this graph, used by the runtime in the same way as a generated one. */
  val schema: Schema by lazy {
    validate()
    frozen = true
    GraphSchema(this, rootNode)
  }

  internal val rootNode: NodeRef by lazy { nodes.values.single { it !in parents } }

  /** A flow without a parameter which finishes with a result of type [R]. */
  protected fun <R : Any> flow(id: String): FlowRef<Unit, R> =
    add(FlowRef(this, id, parameterized = false, nullable = false, included = null))

  /** A flow with a parameter of type [P], a nullable [P] is `null` when the flow is started without a payload. */
  protected inline fun <reified P, R : Any> parameterizedFlow(id: String): FlowRef<P, R> =
    addFlow(id, nullable = null is P)

  protected fun <R : Any> parallel(id: String): ParallelRef<Unit, R> =
    add(ParallelRef(this, id, parameterized = false, nullable = false))

  /** A parallel flow with a parameter of type [P], see [parameterizedFlow]. */
  protected inline fun <reified P, R : Any> parameterizedParallel(id: String): ParallelRef<P, R> =
    addParallel(id, nullable = null is P)

  protected fun screen(id: String): ScreenRef = add(ScreenRef(this, id))

  protected fun <P> parameterizedScreen(id: String): ParameterizedScreenRef<P> = add(ParameterizedScreenRef(this, id))

  /** A shallow history node, the counterpart of a `type = history` node of a `.dot` file. */
  protected fun history(id: String): HistoryRef = add(HistoryRef(this, id, deep = false))

  /** A deep history node, the counterpart of a `type = deepHistory` node of a `.dot` file. */
  protected fun deepHistory(id: String): HistoryRef = add(HistoryRef(this, id, deep = true))

  /**
   * A node which is the graph whose root is [root], the counterpart of a `type = schema` node of a `.dot` file. It has
   * the parameter and the result of [root].
   */
  protected fun <P, R : Any> include(id: String, root: FlowNodeRef<P, R>): FlowRef<P, R> {
    root.graph.schema // freezes the included graph
    require(root.graph.rootNode === root) { "'${root.id}' is not the root of graph '${root.graph.id}'" }
    return add(FlowRef(this, id, root.parameterized, root.nullable, included = root.graph))
  }

  @PublishedApi
  internal fun <P, R : Any> addFlow(id: String, nullable: Boolean): FlowRef<P, R> =
    add(FlowRef(this, id, parameterized = true, nullable = nullable, included = null))

  @PublishedApi
  internal fun <P, R : Any> addParallel(id: String, nullable: Boolean): ParallelRef<P, R> =
    add(ParallelRef(this, id, parameterized = true, nullable = nullable))

  /** Declares an edge from this node to [child] and returns [child], so `a to b to c` reads as `a -> b -> c`. */
  protected infix fun <T : NodeRef> NodeRef.to(child: T): T {
    check(!frozen) { "graph '${this@Graph.id}' is frozen, edges are declared in its init block" }
    require(graph === this@Graph && child.graph === this@Graph) {
      "edge '${this.id}' -> '${child.id}' uses a node of another graph than '${this@Graph.id}'"
    }
    val childParents = parents.getOrPut(child) { mutableListOf() }
    require(this !in childParents) { "edge '${this.id}' -> '${child.id}' is declared twice" }
    childParents.add(this)
    children.getOrPut(this) { mutableListOf() }.add(child)
    return child
  }

  /**
   * A [NodeBuilder] which creates the nodes of this graph with the factories registered in [configure]. Every node
   * but a history one needs a factory.
   */
  fun nodeBuilder(configure: NodeFactories.() -> Unit): NodeBuilder {
    schema // freezes the graph
    val factories = NodeFactories(this).apply(configure).factories
    val missing = nodes.values.filter { it !in factories && it !is HistoryRef }
    require(missing.isEmpty()) { "graph '$id' has no factories for nodes: ${missing.joinToString { it.id }}" }
    return GraphNodeBuilder(this, factories)
  }

  internal fun childrenOf(node: NodeRef): List<NodeRef> = children[node].orEmpty()

  internal fun parentsOf(node: NodeRef): List<NodeRef> = parents[node].orEmpty()

  internal fun parentOf(node: NodeRef): NodeRef? = parents[node]?.first()

  internal fun nodeBySegment(segment: Segment): NodeRef? =
    nodes[segment.id.substringBefore('@')]?.takeIf { it.segment == segment }

  /**
   * `true` for a node which is the root of a schema of its own, as a virtual sub-schema of a `.dot` file: a parallel
   * flow which is not the root and a flow which is a region of a parallel flow.
   */
  internal fun ownsSchema(node: NodeRef): Boolean = (node is ParallelRef<*, *> && node in parents) ||
    (node is FlowRef<*, *> && node.included == null && parentOf(node) is ParallelRef<*, *>)

  /** [node] followed by its ancestors, up to the root (through the first parent of a node with several parents). */
  private fun ancestry(node: NodeRef): Sequence<NodeRef> = generateSequence(node, ::parentOf)

  private fun <T : NodeRef> add(node: T): T {
    check(!frozen) { "graph '$id' is frozen, nodes are declared as its properties" }
    require(node.id.isNotBlank() && '@' !in node.id) { "illegal node id '${node.id}' in graph '$id'" }
    require(nodes.put(node.id, node) == null) { "node '${node.id}' is declared twice in graph '$id'" }
    return node
  }

  private fun validate() {
    nodes.values.filterIsInstance<HistoryRef>().forEach { history ->
      require(history in parents) {
        "history node '${history.id}' of graph '$id' has no parent flow, declare it under one flow with an edge"
      }
    }
    val roots = nodes.values.filter { it !in parents }
    require(roots.size == 1) { "graph '$id' must have one root node (without a parent), has: ${roots.map { it.id }}" }
    val root = roots.single()
    val reachable = generateSequence(listOf(root)) { level -> level.flatMap(::childrenOf).ifEmpty { null } }
      .flatten().toSet()
    require(reachable.size == nodes.size) {
      "graph '$id' has nodes in a cycle: ${nodes.values.filter { it !in reachable }.map { it.id }}"
    }
    require((root is FlowRef<*, *> && root.included == null) || root is ParallelRef<*, *>) {
      "the root '${root.id}' of graph '$id' must be a flow or a parallel flow"
    }
    require(root is ParallelRef<*, *> || childrenOf(root).isNotEmpty()) {
      "flow '${root.id}' of graph '$id' has no children, a flow must have at least one child screen or flow"
    }
    val fanIn = nodes.values.filter { parentsOf(it).size > 1 && (it !is FlowRef<*, *> || it.included == null) }
    require(fanIn.isEmpty()) {
      "nodes of graph '$id' have several parents (fan-in): ${fanIn.joinToString { it.id }}, only an included " +
        "graph can have several parents"
    }
    nodes.values.forEach { node ->
      when (node) {
        is ParallelRef<*, *> -> validateParallel(node)
        is FlowRef<*, *> -> if (node.included != null) validateInclude(node)
        is HistoryRef -> validateHistory(node)
        is ScreenRef, is ParameterizedScreenRef<*> -> Unit
      }
    }
  }

  private fun validateParallel(parallel: ParallelRef<*, *>) {
    val regions = childrenOf(parallel).filter { it !is HistoryRef }
    require(regions.isNotEmpty()) {
      "parallel '${parallel.id}' of graph '$id' has no children, a parallel flow must have at least one child flow"
    }
    require(regions.none { it is ScreenRef || it is ParameterizedScreenRef<*> }) {
      "the children of parallel '${parallel.id}' in graph '$id' must be flows, parallel flows or included graphs"
    }
  }

  private fun validateInclude(include: FlowRef<*, *>) {
    require(childrenOf(include).isEmpty()) { "included graph '${include.id}' in graph '$id' can't have children" }
    val includeParents = parentsOf(include)
    if (includeParents.size < 2) return
    includeParents.firstOrNull { it is ParallelRef<*, *> }?.let { parallel ->
      throw IllegalArgumentException(
        "included graph '${include.id}' in graph '$id' has several parents, one of which is the parallel " +
          "'${parallel.id}'; enter it through a flow or a screen instead",
      )
    }
    val owners = includeParents.map { parent -> ancestry(parent).firstOrNull(::ownsSchema) }
    require(owners.distinct().size == 1) {
      "included graph '${include.id}' in graph '$id' has parents in different parallel regions " +
        "(${includeParents.joinToString { it.id }}); move the parents into one region"
    }
  }

  private fun validateHistory(history: HistoryRef) {
    require(childrenOf(history).isEmpty()) {
      "history node '${history.id}' of graph '$id' must be a leaf, but has children: " +
        childrenOf(history).joinToString { it.id }
    }
    val parent = parentOf(history)
    when {
      parent is FlowRef<*, *> && parent.included == null -> Unit

      parent is ParallelRef<*, *> -> require(ancestry(parent).drop(1).any { it is FlowRef<*, *> }) {
        "history node '${history.id}' of graph '$id' is under the parallel '${parent.id}' with no flow above it to " +
          "host its target; nest the parallel under a flow"
      }

      else -> throw IllegalArgumentException(
        "history node '${history.id}' of graph '$id' must be a child of a flow or a parallel flow, but its parent " +
          "'${parent?.id}' is not; history under an included graph or a screen is not supported",
      )
    }
  }
}

sealed class NodeRef(
  internal val graph: Graph,
  val id: String,
  internal val parameterized: Boolean,
  internal val nullable: Boolean = false,
) {
  val segment: Segment = Segment("$id@${graph.id}")

  override fun toString(): String = segment.id

  /**
   * The path from the closest flow above this node, as in a generated target. A node with several parents needs
   * [via], the parent it is entered from.
   */
  internal fun targetPath(via: NodeRef? = null): Path {
    val parents = graph.parentsOf(this)
    val parent = when {
      via != null -> via.also { require(it in parents) { "'${it.id}' is not a parent of '$id'" } }

      parents.size > 1 -> throw IllegalArgumentException(
        "'$id' has several parents (${parents.joinToString { it.id }}), pass the one to enter it from as `via`",
      )

      else -> parents.firstOrNull()
    }
    val segments = ArrayDeque(listOf(segment))
    var screen = parent
    while (screen is ScreenRef || screen is ParameterizedScreenRef<*>) {
      segments.addFirst(screen.segment)
      screen = graph.parentOf(screen)
    }
    return Path(segments)
  }

  /**
   * [FlowTarget.ancestorPayloads] / [ScreenTarget.ancestorPayloads] of a full target: every one of [ancestors] must be a
   * parameterized ancestor of this node (entered through [via]).
   */
  internal fun ancestorPayloads(ancestors: Array<out AncestorPayload>, via: NodeRef? = null): Map<Segment, Any?> {
    if (ancestors.isEmpty()) return emptyMap()
    val above = generateSequence(via ?: graph.parentsOf(this).firstOrNull(), graph::parentOf).toSet()
    return ancestors.associate {
      require(it.ref.parameterized && it.ref in above) { "'${it.ref.id}' is not a parameterized ancestor of '$id'" }
      it.ref.segment to it.value
    }
  }
}

/** A payload of a parameterized ancestor in a full target, made with [with]. */
class AncestorPayload internal constructor(internal val ref: NodeRef, internal val value: Any?)

/** The [payload] of this parameterized flow when it is an ancestor in a full target. */
infix fun <P> FlowNodeRef<P, *>.with(payload: P): AncestorPayload = AncestorPayload(this, payload)

/** The [payload] of this parameterized screen when it is an ancestor in a full target. */
infix fun <P> ParameterizedScreenRef<P>.with(payload: P): AncestorPayload = AncestorPayload(this, payload)

/** A flow or a parallel flow with a parameter of type [P] (`Unit` for none) and a result of type [R]. */
sealed class FlowNodeRef<P, R : Any>(graph: Graph, id: String, parameterized: Boolean, nullable: Boolean) :
  NodeRef(graph, id, parameterized, nullable) {
  /**
   * A target which starts this flow with [payload]. An included graph with several parents needs [via], the parent
   * it is entered from (a generated `<id>Via<Parent>` target). Payloads of parameterized [ancestors]
   * (`shop with shopId`) make it a full target, which rebuilds them when they are not alive, like a generated full target.
   */
  fun target(payload: P, vararg ancestors: AncestorPayload, via: NodeRef? = null): FlowTarget = FlowTarget(
    targetPath(via),
    if (parameterized) payload ?: NullPayload else null,
    ancestorPayloads(ancestors, via),
  )
}

/** A target of a flow without a parameter, see [FlowNodeRef.target]. */
fun FlowNodeRef<Unit, *>.target(vararg ancestors: AncestorPayload, via: NodeRef? = null): FlowTarget =
  target(Unit, *ancestors, via = via)

class FlowRef<P, R : Any> internal constructor(
  graph: Graph,
  id: String,
  parameterized: Boolean,
  nullable: Boolean,
  internal val included: Graph?,
) : FlowNodeRef<P, R>(graph, id, parameterized, nullable)

class ParallelRef<P, R : Any> internal constructor(
  graph: Graph,
  id: String,
  parameterized: Boolean,
  nullable: Boolean,
) : FlowNodeRef<P, R>(graph, id, parameterized, nullable) {
  /**
   * Id of the region which starts at [child], relative to the schema of this parallel flow, like a generated
   * `<child>RegionId`. Use it in `initialRegions`, `startRegion`, `DispatchBackTo` and `NodeHost(regionId = ...)`.
   */
  fun regionId(child: FlowNodeRef<*, *>): RegionId {
    graph.schema // validates and freezes the graph
    require(this in graph.parentsOf(child)) { "'${child.id}' is not a region of parallel '$id'" }
    return RegionId(Path(listOf(segment, child.segment)))
  }
}

class ScreenRef internal constructor(graph: Graph, id: String) : NodeRef(graph, id, parameterized = false) {
  /** A target of this screen, a full one with payloads of parameterized [ancestors], see [FlowNodeRef.target]. */
  fun target(vararg ancestors: AncestorPayload): ScreenTarget =
    ScreenTarget(targetPath(), ancestorPayloads = ancestorPayloads(ancestors))
}

class ParameterizedScreenRef<P> internal constructor(graph: Graph, id: String) :
  NodeRef(graph, id, parameterized = true) {
  /** A target of this screen, a full one with payloads of parameterized [ancestors], see [FlowNodeRef.target]. */
  fun target(payload: P, vararg ancestors: AncestorPayload): ScreenTarget =
    ScreenTarget(targetPath(), payload ?: NullPayload, ancestorPayloads(ancestors))
}

/**
 * A history node: [target] re-enters its parent with the nodes which were active when the parent was left (only the
 * direct child of the parent for a shallow history, every node below it for a [deep] one).
 */
class HistoryRef internal constructor(graph: Graph, id: String, val deep: Boolean) :
  NodeRef(graph, id, parameterized = false) {
  /**
   * The target of the parent of this node. Its path starts at the root of the graph and comes after [prefix] (the
   * absolute path above the graph, if any), as the generated history target does.
   */
  fun target(prefix: Path? = null): HistoryTarget {
    val path = Path(generateSequence(graph.parentOf(this), graph::parentOf).map { it.segment }.toList().asReversed())
    return HistoryTarget(prefix?.append(path) ?: path, deep)
  }
}

/**
 * Sent to the parent of a flow (or of a region of a parallel flow) declared by a [Graph] when it finishes, the
 * counterpart of a generated `ChildFinishRequest`. Read its result with [childFlowResult].
 */
data class ChildFlowFinishedEvent(val child: Segment, val result: Any) : Event

/** The result of [child] if this event reports that [child] has finished, `null` otherwise. */
@Suppress("UNCHECKED_CAST")
fun <C : Any> Event.childFlowResult(child: FlowNodeRef<*, C>): C? =
  if (this is ChildFlowFinishedEvent && this.child == child.segment) result as C else null
