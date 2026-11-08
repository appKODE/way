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

  /** [factory] gets the payload of the flow, `null` for a flow without a parameter. */
  fun flow(ref: FlowRef<*>, factory: (payload: Any?) -> FlowNode<*>) {
    require(ref.included == null) { "'${ref.id}' is an included graph, register it with include()" }
    put(ref, factory)
  }

  fun parallel(ref: ParallelRef, factory: (payload: Any?) -> ParallelFlowNode<*>) = put(ref, factory)

  fun screen(ref: ScreenRef, factory: () -> ScreenNode) = put(ref) { factory() }

  @Suppress("UNCHECKED_CAST")
  fun <P> screen(ref: ParameterizedScreenRef<P>, factory: (payload: P) -> ScreenNode) = put(ref) { factory(it as P) }

  /** [factory] creates the [NodeBuilder] of the included graph, it gets the payload of the graph's root. */
  fun include(ref: FlowRef<*>, factory: (payload: Any?) -> NodeBuilder) {
    require(ref.included != null) { "'${ref.id}' is not an included graph, register it with flow()" }
    put(ref, factory)
  }

  private fun put(ref: NodeRef, factory: (Any?) -> Any) {
    require(ref.graph === graph) { "node '$ref' does not belong to graph '${graph.id}'" }
    factories[ref] = factory
  }
}

internal class GraphSchema(private val graph: Graph) : Schema {
  override val rootSegment: Segment = graph.rootNode.segment

  override val childSchemas: Map<Segment, Schema> = collectNodes(graph.rootNode)
    .mapNotNull { node -> (node as? FlowRef<*>)?.included?.let { node.segment to it.schema } }
    .toMap()

  override val regions: List<RegionId> = if (graph.rootNode is ParallelRef) {
    graph.childrenOf(graph.rootNode).map { RegionId(Path(listOf(rootSegment, it.segment))) }
  } else {
    listOf(RegionId(Path(rootSegment)))
  }

  override fun target(regionId: RegionId, segment: Segment, rootSegmentAlias: Segment?): Path? {
    checkRegion(regionId)
    return graph.nodeBySegment(segment)?.let { pathOf(it, rootSegmentAlias) }
  }

  override fun nodeType(regionId: RegionId, path: Path, rootSegmentAlias: Segment?): Schema.NodeType =
    when (nodeAt(regionId, path, rootSegmentAlias)) {
      is FlowRef<*> -> Schema.NodeType.Flow
      is ParallelRef -> Schema.NodeType.ParallelFlow
      is ScreenRef, is ParameterizedScreenRef<*> -> Schema.NodeType.Screen
    }

  override fun isParameterized(regionId: RegionId, path: Path, rootSegmentAlias: Segment?): Boolean =
    nodeAt(regionId, path, rootSegmentAlias).parameterized

  override fun createChildFlowFinishRequestEvent(regionId: RegionId, path: Path, result: Any): Event {
    checkRegion(regionId)
    return ChildFlowFinishedEvent(path.lastSegment(), result)
  }

  /** The path of [node] from the root of the graph, the root is replaced with [rootSegmentAlias]. */
  private fun pathOf(node: NodeRef, rootSegmentAlias: Segment?): Path {
    val segments = generateSequence(node, graph::parentOf).map { it.segment }.toMutableList()
    segments[segments.lastIndex] = rootSegmentAlias ?: rootSegment
    return Path(segments.asReversed())
  }

  private fun nodeAt(regionId: RegionId, path: Path, rootSegmentAlias: Segment?): NodeRef {
    checkRegion(regionId)
    val node = if (path.length == 1) graph.rootNode else graph.nodeBySegment(path.lastSegment())
    return node?.takeIf { pathOf(it, rootSegmentAlias) == path } ?: error("internal error: no node for path=$path")
  }

  private fun checkRegion(regionId: RegionId) = check(regionId in regions) { "unknown regionId=$regionId" }

  private fun collectNodes(node: NodeRef): List<NodeRef> = listOf(node) + graph.childrenOf(node).flatMap(::collectNodes)
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
      if (node is FlowRef<*> && node.included != null) {
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

  private fun payload(node: NodeRef, path: Path, payloads: Map<Path, Any>): Any? {
    if (!node.parameterized) return null
    val payload = payloads[path] ?: throw MissingPayloadException(path)
    return payload.takeUnless { it === NullPayload }
  }
}
