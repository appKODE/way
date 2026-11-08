package ru.kode.way.dsl

import ru.kode.way.Event
import ru.kode.way.FlowTarget
import ru.kode.way.NodeBuilder
import ru.kode.way.NullPayload
import ru.kode.way.Path
import ru.kode.way.Schema
import ru.kode.way.ScreenTarget
import ru.kode.way.Segment

/**
 * A navigation graph declared in Kotlin, the counterpart of a `.dot` file which needs no Gradle plugin. Nodes are
 * properties of a subclass, edges are declared with [to] in its `init` block:
 *
 * ```kotlin
 * object LoginGraph : Graph("Login") {
 *   val login = flow<LoginResult>("login")
 *   val credentials = screen("credentials")
 *   val otp = parameterizedScreen<String>("otp")
 *
 *   init {
 *     login to credentials to otp
 *   }
 * }
 * ```
 *
 * The node without a parent is the root, it is a flow or a parallel flow. A parallel flow can only be the root and its
 * children (the regions) are [include]d graphs. Nodes are identified by `id@graphId`, so [id] has to be unique among
 * the graphs of a [ru.kode.way.NavigationService].
 *
 * The graph is checked and frozen on the first access to [schema] or [nodeBuilder].
 */
abstract class Graph(val id: String) {
  private val nodes = LinkedHashMap<String, NodeRef>()
  private val parents = HashMap<NodeRef, NodeRef>()
  private val children = HashMap<NodeRef, MutableList<NodeRef>>()
  private var frozen = false

  /** The [Schema] of this graph, used by the runtime in the same way as a generated one. */
  val schema: Schema by lazy {
    validate()
    frozen = true
    GraphSchema(this)
  }

  internal val rootNode: NodeRef by lazy { nodes.values.single { it !in parents } }

  protected fun <R : Any> flow(id: String, parameterized: Boolean = false): FlowRef<R> =
    add(FlowRef(this, id, parameterized, included = null))

  protected fun parallel(id: String, parameterized: Boolean = false): ParallelRef =
    add(ParallelRef(this, id, parameterized))

  protected fun screen(id: String): ScreenRef = add(ScreenRef(this, id))

  protected fun <P> parameterizedScreen(id: String): ParameterizedScreenRef<P> = add(ParameterizedScreenRef(this, id))

  /** A node which is the root of [graph], the counterpart of a `type = schema` node of a `.dot` file. */
  protected fun <R : Any> include(id: String, graph: Graph): FlowRef<R> {
    graph.schema // freezes the included graph
    return add(FlowRef(this, id, graph.rootNode.parameterized, included = graph))
  }

  /** Declares an edge from this node to [child] and returns [child], so `a to b to c` reads as `a -> b -> c`. */
  protected infix fun <T : NodeRef> NodeRef.to(child: T): T {
    check(!frozen) { "graph '${this@Graph.id}' is frozen, edges are declared in its init block" }
    require(graph === this@Graph && child.graph === this@Graph) {
      "edge '${this.id}' -> '${child.id}' uses a node of another graph than '${this@Graph.id}'"
    }
    val previous = parents.put(child, this)
    require(previous == null) { "node '${child.id}' has several parents ('${previous?.id}', '${this.id}')" }
    children.getOrPut(this) { mutableListOf() }.add(child)
    return child
  }

  /**
   * A [NodeBuilder] which creates the nodes of this graph with the factories registered in [configure]. Every node
   * needs a factory.
   */
  fun nodeBuilder(configure: NodeFactories.() -> Unit): NodeBuilder {
    schema // freezes the graph
    val factories = NodeFactories(this).apply(configure).factories
    val missing = nodes.values.filter { it !in factories }
    require(missing.isEmpty()) { "graph '$id' has no factories for nodes: ${missing.joinToString { it.id }}" }
    return GraphNodeBuilder(this, factories)
  }

  internal fun childrenOf(node: NodeRef): List<NodeRef> = children[node].orEmpty()

  internal fun parentOf(node: NodeRef): NodeRef? = parents[node]

  internal fun nodeBySegment(segment: Segment): NodeRef? =
    nodes[segment.id.substringBefore('@')]?.takeIf { it.segment == segment }

  private fun <T : NodeRef> add(node: T): T {
    check(!frozen) { "graph '$id' is frozen, nodes are declared as its properties" }
    require(node.id.isNotBlank() && '@' !in node.id) { "illegal node id '${node.id}' in graph '$id'" }
    require(nodes.put(node.id, node) == null) { "node '${node.id}' is declared twice in graph '$id'" }
    return node
  }

  private fun validate() {
    val roots = nodes.values.filter { it !in parents }
    require(roots.size == 1) { "graph '$id' must have one root node (without a parent), has: ${roots.map { it.id }}" }
    val root = roots.single()
    val reachable = generateSequence(listOf(root)) { level -> level.flatMap(::childrenOf).ifEmpty { null } }
      .flatten().toSet()
    require(reachable.size == nodes.size) {
      "graph '$id' has nodes in a cycle: ${nodes.values.filter { it !in reachable }.map { it.id }}"
    }
    require((root is FlowRef<*> && root.included == null) || root is ParallelRef) {
      "the root '${root.id}' of graph '$id' must be a flow or a parallel flow"
    }
    nodes.values.forEach { node ->
      if (node is ParallelRef) {
        require(node === root) { "parallel '${node.id}' of graph '$id' must be the root, include a graph instead" }
        require(childrenOf(node).isNotEmpty() && childrenOf(node).all { it is FlowRef<*> && it.included != null }) {
          "the children of parallel '${node.id}' in graph '$id' must be included graphs"
        }
      }
      if (node is FlowRef<*> && node.included != null) {
        require(childrenOf(node).isEmpty()) { "included graph '${node.id}' in graph '$id' can't have children" }
      }
    }
  }
}

sealed class NodeRef(internal val graph: Graph, val id: String, internal val parameterized: Boolean) {
  val segment: Segment = Segment("$id@${graph.id}")

  override fun toString(): String = segment.id

  /** The path from the closest flow above this node, as in a generated target. */
  internal fun targetPath(): Path {
    val segments = ArrayDeque(listOf(segment))
    var parent = graph.parentOf(this)
    while (parent is ScreenRef || parent is ParameterizedScreenRef<*>) {
      segments.addFirst(parent.segment)
      parent = graph.parentOf(parent)
    }
    return Path(segments)
  }
}

class FlowRef<R : Any> internal constructor(
  graph: Graph,
  id: String,
  parameterized: Boolean,
  internal val included: Graph?,
) : NodeRef(graph, id, parameterized) {
  /** [payload] is the argument of a parameterized flow, `null` for a nullable parameter. */
  fun target(payload: Any? = null): FlowTarget =
    FlowTarget(targetPath(), if (parameterized) payload ?: NullPayload else null)
}

class ParallelRef internal constructor(graph: Graph, id: String, parameterized: Boolean) :
  NodeRef(graph, id, parameterized)

class ScreenRef internal constructor(graph: Graph, id: String) : NodeRef(graph, id, parameterized = false) {
  fun target(): ScreenTarget = ScreenTarget(targetPath())
}

class ParameterizedScreenRef<P> internal constructor(graph: Graph, id: String) :
  NodeRef(graph, id, parameterized = true) {
  fun target(payload: P): ScreenTarget = ScreenTarget(targetPath(), payload ?: NullPayload)
}

/**
 * Sent to the parent of a flow (or of a region of a parallel flow) declared by a [Graph] when it finishes, the
 * counterpart of a generated `ChildFinishRequest`. Read its result with [childFlowResult].
 */
data class ChildFlowFinishedEvent(val child: Segment, val result: Any) : Event

/** The result of [child] if this event reports that [child] has finished, `null` otherwise. */
@Suppress("UNCHECKED_CAST")
fun <C : Any> Event.childFlowResult(child: FlowRef<C>): C? =
  if (this is ChildFlowFinishedEvent && this.child == child.segment) result as C else null
