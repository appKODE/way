package ru.kode.way.gradle

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import ru.kode.way.gradle.DotParser.GraphContext
import java.io.File

internal fun parseSchemaDotFile(
  file: File,
  packageName: String,
): SchemaParseResult {
  val stream = CommonTokenStream(DotLexer(CharStreams.fromPath(file.toPath())))
  val parser = DotParser(stream)
  val parseTree = parser.graph()
  val visitor = Visitor()
  visitor.visitGraph(parseTree)
  return visitor.buildResult()
}

private class Visitor : DotBaseVisitor<Unit>() {
  private var graphId: String? = null
  private var customSchemaFileName: String? = null

  private val adjacencyList: MutableMap<String, MutableList<String>> = mutableMapOf()
  private val flowNodes: MutableList<String> = mutableListOf()

  fun buildResult(): SchemaParseResult {
    fun String.toNode(): Node {
      return when {
        flowNodes.contains(this) -> Node.Flow(this)
        else -> Node.Screen(this)
      }
    }
    return SchemaParseResult(
      adjacencyList = this.adjacencyList.entries.associate { (nodeId, adjacentIds) ->
        nodeId.toNode() to adjacentIds.map { it.toNode() }
      },
      graphId = graphId,
      customSchemaFileName = customSchemaFileName
    )
  }

  override fun visitGraph(ctx: GraphContext) {
    graphId = ctx.id_()?.text
    customSchemaFileName = findGraphAttributeValue(ctx, "schemaFileName")
    super.visitGraph(ctx)
  }

  private fun findGraphAttributeValue(ctx: GraphContext, name: String): String? {
    for (i in 0 until ctx.stmt_list().childCount) {
      if (ctx.stmt_list().stmt(i).id_(0)?.ID()?.text == name) {
        return ctx.stmt_list().stmt(i).id_(1)?.let {
          it.ID() ?: it.STRING()
        }?.text?.removeSurrounding("\"") ?: error("no value for graph attr '$name'")
      }
    }
    return null
  }

  override fun visitNode_stmt(ctx: DotParser.Node_stmtContext) {
    super.visitNode_stmt(ctx)
    val nodeId = ctx.node_id()?.id_()?.text ?: error("no node id for ${ctx.text}")
    val isFlowNode = ctx.attr_list()?.a_list()
      ?.any { it.id_(0).text == ATTR_NAME_NODE_TYPE && it.id_(1).text == ATTR_VALUE_NODE_TYPE_FLOW } == true
    if (isFlowNode) {
      adjacencyList.getOrPut(nodeId) { mutableListOf() }
      flowNodes.add(nodeId)
    } else {
      println("ignoring non-flow node at: ${ctx.text}")
    }
  }

  override fun visitEdge_stmt(ctx: DotParser.Edge_stmtContext) {
    val nodeId = ctx.node_id().id_().text
    super.visitEdge_stmt(ctx)
    val rhsFirstNode = ctx.edgeRHS().node_id(0).text
    adjacencyList.getOrPut(nodeId) { mutableListOf() }
    adjacencyList.getOrPut(rhsFirstNode) { mutableListOf() }
    adjacencyList[nodeId]?.add(rhsFirstNode)
  }

  override fun visitEdgeRHS(ctx: DotParser.EdgeRHSContext) {
    val nodeIds = ctx.node_id()
    nodeIds.windowed(2).forEach { (id1, id2) ->
      adjacencyList.getOrPut(id1.text) { mutableListOf() }
      adjacencyList.getOrPut(id2.text) { mutableListOf() }
      adjacencyList[id1.text]?.add(id2.text)
    }
    super.visitEdgeRHS(ctx)
  }
}

internal data class SchemaParseResult(
  val graphId: String?,
  val customSchemaFileName: String?,
  val adjacencyList: AdjacencyList,
)

internal typealias AdjacencyList = Map<Node, List<Node>>

internal sealed class Node {
  abstract val id: String

  data class Flow(override val id: String) : Node()
  data class Screen(override val id: String) : Node()
  data class Parallel(override val id: String) : Node()
}

private const val ATTR_NAME_NODE_TYPE = "type"
private const val ATTR_VALUE_NODE_TYPE_FLOW = "flow"
