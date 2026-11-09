package ru.kode.way.dsl

import ru.kode.way.Event
import ru.kode.way.FlowNode
import ru.kode.way.MissingPayloadException
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.NullPayload
import ru.kode.way.ParallelFlowNode
import ru.kode.way.Path
import ru.kode.way.RegionId
import ru.kode.way.Schema
import ru.kode.way.ScreenNode
import ru.kode.way.Segment
import ru.kode.way.drop
import ru.kode.way.firstSegment
import ru.kode.way.lastSegment
import ru.kode.way.startsWith
import ru.kode.way.take

/** Registers a factory for every node of a [Graph], see [Graph.nodeBuilder]. */
class NodeFactories internal constructor(private val graph: Graph) {
  internal val factories = HashMap<NodeRef, (Any?) -> Any>()

  /** [factory] gets the payload of the flow, `Unit` for a flow without a parameter. */
  fun <P, R : Any> flow(ref: FlowRef<P, R>, factory: (payload: P) -> FlowNode<R>) {
    require(ref.included == null) { "'${ref.id}' is an included graph, register it with include()" }
    put(ref, factory)
  }

  /** [factory] gets the payload of the parallel flow, `Unit` for a parallel flow without a parameter. */
  fun <P, R : Any> parallel(ref: ParallelRef<P, R>, factory: (payload: P) -> ParallelFlowNode<R>) = put(ref, factory)

  fun screen(ref: ScreenRef, factory: () -> ScreenNode) = put(ref) { _: Any? -> factory() }

  fun <P> screen(ref: ParameterizedScreenRef<P>, factory: (payload: P) -> ScreenNode) = put(ref, factory)

  /** [factory] creates the [NodeBuilder] of the included graph, it gets the payload of the graph's root. */
  fun <P> include(ref: FlowRef<P, *>, factory: (payload: P) -> NodeBuilder) {
    require(ref.included != null) { "'${ref.id}' is not an included graph, register it with flow()" }
    put(ref, factory)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <P> put(ref: NodeRef, factory: (P) -> Any) {
    require(ref.graph === graph) { "node '$ref' does not belong to graph '${graph.id}'" }
    factories[ref] = { payload -> factory(payload as P) }
  }
}

/**
 * The schema of the part of a [Graph] under [root], the graph's root or a node which owns a schema (see
 * [Graph.ownsSchema]), as a generated schema or virtual sub-schema is. History nodes are not part of it: they are never
 * built, their target is the one of their parent.
 */
internal class GraphSchema(private val graph: Graph, private val root: NodeRef) : Schema {
  private val nodes: Set<NodeRef> = LinkedHashSet<NodeRef>().also { collect(root, it) }

  override val rootSegment: Segment = root.segment

  override val childSchemas: Map<Segment, Schema> = nodes.filter { it !== root }.mapNotNull { node ->
    val included = (node as? FlowRef<*, *>)?.included
    when {
      included != null -> node.segment to included.schema
      graph.ownsSchema(node) -> node.segment to GraphSchema(graph, node)
      else -> null
    }
  }.toMap()

  /**
   * The root unless it is a parallel flow, then the children of every parallel flow which is not a child of another
   * one (the regions of that one), as in a generated schema.
   */
  override val regions: List<RegionId> = buildList {
    if (root !is ParallelRef<*, *>) add(root)
    nodes.filter { it is ParallelRef<*, *> && (it === root || graph.parentOf(it) !is ParallelRef<*, *>) }
      .forEach { parallel -> addAll(graph.childrenOf(parallel).filter { it in nodes }) }
  }.map { RegionId(pathsOf(it, rootSegmentAlias = null).single()) }

  override fun target(regionId: RegionId, segment: Segment, rootSegmentAlias: Segment?): Path? =
    targets(regionId, segment, rootSegmentAlias).firstOrNull()

  override fun targets(regionId: RegionId, segment: Segment, rootSegmentAlias: Segment?): List<Path> {
    checkRegion(regionId)
    return graph.nodeBySegment(segment)?.takeIf { it in nodes }?.let { pathsOf(it, rootSegmentAlias) }.orEmpty()
  }

  override fun nodeType(regionId: RegionId, path: Path, rootSegmentAlias: Segment?): Schema.NodeType =
    when (nodeAt(regionId, path, rootSegmentAlias)) {
      is FlowRef<*, *> -> Schema.NodeType.Flow
      is ParallelRef<*, *> -> Schema.NodeType.ParallelFlow
      is ScreenRef, is ParameterizedScreenRef<*> -> Schema.NodeType.Screen
      is HistoryRef -> error("internal error: history node at path=$path")
    }

  /** A nullable root is built with `null` when it has no payload, so it never misses one. */
  override fun isParameterized(regionId: RegionId, path: Path, rootSegmentAlias: Segment?): Boolean {
    val node = nodeAt(regionId, path, rootSegmentAlias)
    return node.parameterized && !(node === root && node.nullable)
  }

  override fun createChildFlowFinishRequestEvent(regionId: RegionId, path: Path, result: Any): Event {
    checkRegion(regionId)
    return ChildFlowFinishedEvent(path.lastSegment(), result)
  }

  /** The paths of [node] from [root], one per parent, the root is replaced with [rootSegmentAlias]. */
  private fun pathsOf(node: NodeRef, rootSegmentAlias: Segment?): List<Path> = if (node === root) {
    listOf(Path(rootSegmentAlias ?: rootSegment))
  } else {
    graph.parentsOf(node).flatMap { pathsOf(it, rootSegmentAlias) }.map { Path(it.segments + node.segment) }
  }

  private fun nodeAt(regionId: RegionId, path: Path, rootSegmentAlias: Segment?): NodeRef {
    checkRegion(regionId)
    val node = if (path.length == 1) root else graph.nodeBySegment(path.lastSegment())?.takeIf { it in nodes }
    return node?.takeIf { path in pathsOf(it, rootSegmentAlias) } ?: error("internal error: no node for path=$path")
  }

  private fun checkRegion(regionId: RegionId) = check(regionId in regions) { "unknown regionId=$regionId" }

  private fun collect(node: NodeRef, into: MutableSet<NodeRef>) {
    if (node !is HistoryRef && into.add(node)) graph.childrenOf(node).forEach { collect(it, into) }
  }
}

/**
 * Builds the nodes of a [Graph] as a generated node builder does: a node is created on every request (the runtime
 * keeps alive nodes), the node builders of included graphs are cached while some alive node is inside of them.
 */
internal class GraphNodeBuilder(private val graph: Graph, private val factories: Map<NodeRef, (Any?) -> Any>) :
  NodeBuilder {
  override val schema: GraphSchema = graph.schema as GraphSchema

  private val nodeBuilders = HashMap<Path, NodeBuilder>()

  override fun build(path: Path, payloads: Map<Path, Any>, rootSegmentAlias: Segment?): Node {
    val rootSegment = rootSegmentAlias ?: schema.rootSegment
    check(path.firstSegment() == rootSegment) { "illegal path build requested for graph '${graph.id}': $path" }
    var node = graph.rootNode
    for (index in 1 until path.length) {
      node = graph.childrenOf(node).find { it.segment == path.segments[index] }
        ?: error("illegal path build requested for graph '${graph.id}': $path")
      if (node is FlowRef<*, *> && node.included != null) {
        val includePath = path.take(index + 1)
        val included = node
        val nodeBuilder = nodeBuilders.getOrPut(includePath) {
          factories.getValue(included)(payload(included, includePath, payloads)) as NodeBuilder
        }
        return nodeBuilder.build(
          path.drop(index),
          payloads.filterKeys { it.length > index }.mapKeys { it.key.drop(index) },
          rootSegmentAlias = path.segments[index],
        )
      }
    }
    return factories.getValue(node)(payload(node, path, payloads)) as Node
  }

  override fun invalidateCache(alivePaths: Set<Path>) {
    nodeBuilders.keys.retainAll { key -> alivePaths.any { it.startsWith(key) } }
    nodeBuilders.forEach { (builderPath, builder) ->
      val drop = builderPath.length - 1
      builder.invalidateCache(alivePaths.filter { it.startsWith(builderPath) }.map { it.drop(drop) }.toSet())
    }
  }

  override fun snapshotCache(): Any? = nodeBuilders.mapValues { (_, builder) -> builder to builder.snapshotCache() }

  override fun restoreCache(snapshot: Any?) {
    nodeBuilders.clear()
    (snapshot as Map<*, *>).forEach { (path, saved) ->
      val (builder, childSnapshot) = saved as Pair<*, *>
      nodeBuilders[path as Path] = builder as NodeBuilder
      builder.restoreCache(childSnapshot)
    }
  }

  /** `Unit` for a node without a parameter, `null` for a missing payload of a nullable flow (see [GraphSchema]). */
  private fun payload(node: NodeRef, path: Path, payloads: Map<Path, Any>): Any? {
    if (!node.parameterized) return Unit
    val payload = payloads[path] ?: if (node.nullable) return null else throw MissingPayloadException(path)
    return payload.takeUnless { it === NullPayload }
  }
}
