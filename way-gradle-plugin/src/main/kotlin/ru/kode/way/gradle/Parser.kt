package ru.kode.way.gradle

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import ru.kode.way.gradle.DotParser.GraphContext
import ru.kode.way.gradle.DotParser.Id_Context
import java.io.File

internal fun parseSchemaDotFile(
  file: File,
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
  private var customTargetsFileName: String? = null
  private var customPackage: String? = null

  private val adjacencyList: MutableMap<String, MutableList<String>> = mutableMapOf()
  private val flowNodes: MutableList<String> = mutableListOf()
  private val schemaNodes: MutableList<String> = mutableListOf()
  private val flowNodeResultTypes: MutableMap<String, String> = mutableMapOf()

  fun buildResult(): SchemaParseResult {
    fun String.toNode(): Node {
      return when {
        flowNodes.contains(this) -> Node.Flow.Local(this, flowNodeResultTypes[this] ?: "kotlin.Unit")
        schemaNodes.contains(this) -> Node.Flow.Imported(this, flowNodeResultTypes[this] ?: "kotlin.Unit")
        else -> Node.Screen(this)
      }
    }
    return SchemaParseResult(
      adjacencyList = this.adjacencyList.entries.associate { (nodeId, adjacentIds) ->
        nodeId.toNode() to adjacentIds.map { it.toNode() }
      },
      graphId = graphId,
      customSchemaFileName = customSchemaFileName,
      customTargetsFileName = customTargetsFileName,
      customPackage = customPackage,
    )
  }

  override fun visitGraph(ctx: GraphContext) {
    graphId = ctx.id_()?.asString()
    customSchemaFileName = findGraphAttributeValue(ctx, "schemaFileName")
    customTargetsFileName = findGraphAttributeValue(ctx, "targetsFileName")
    customPackage = findGraphAttributeValue(ctx, "package")
    super.visitGraph(ctx)
  }

  private fun findGraphAttributeValue(ctx: GraphContext, name: String): String? {
    for (i in 0 until ctx.stmt_list().childCount) {
      if (ctx.stmt_list().stmt(i).id_(0)?.asString() == name) {
        return ctx.stmt_list().stmt(i).id_(1)?.asString() ?: error("no value for graph attr '$name'")
      }
    }
    return null
  }

  override fun visitNode_stmt(ctx: DotParser.Node_stmtContext) {
    super.visitNode_stmt(ctx)
    val nodeId = ctx.node_id()?.id_()?.asString() ?: error("no node id for ${ctx.text}")
    val attrs = ctx.attr_list()?.a_list(0)?.id_()?.chunked(2).orEmpty()
    val isFlowNode = attrs
      .any { (id, value) -> id.asString() == ATTR_NAME_NODE_TYPE && value.asString() == ATTR_VALUE_NODE_TYPE_FLOW }
    val isSchemaNode = attrs
      .any { (id, value) -> id.asString() == ATTR_NAME_NODE_TYPE && value.asString() == ATTR_VALUE_NODE_TYPE_SCHEMA }
    if (isFlowNode) {
      adjacencyList.getOrPut(nodeId) { mutableListOf() }
      flowNodes.add(nodeId)
      val resultType = attrs
        .find { (id, _) -> id.asString() == ATTR_NAME_FLOW_RESULT_TYPE }
        ?.get(1)
        ?.asString()
      if (resultType != null) {
        flowNodeResultTypes[nodeId] = resultType
      }
    } else if (isSchemaNode) {
      adjacencyList.getOrPut(nodeId) { mutableListOf() }
      schemaNodes.add(nodeId)
      val resultType = attrs
        .find { (id, _) -> id.asString() == ATTR_NAME_FLOW_RESULT_TYPE }
        ?.get(1)
        ?.asString()
      if (resultType != null) {
        flowNodeResultTypes[nodeId] = resultType
      }
    } else {
      println("ignoring non-flow node at: ${ctx.text}")
    }
  }

  override fun visitEdge_stmt(ctx: DotParser.Edge_stmtContext) {
    val nodeId = ctx.node_id().id_().asString()
    super.visitEdge_stmt(ctx)
    val rhsFirstNode = ctx.edgeRHS().node_id(0).id_().asString()
    adjacencyList.getOrPut(nodeId) { mutableListOf() }
    adjacencyList.getOrPut(rhsFirstNode) { mutableListOf() }
    adjacencyList[nodeId]?.add(rhsFirstNode)
  }

  override fun visitEdgeRHS(ctx: DotParser.EdgeRHSContext) {
    val nodeIds = ctx.node_id()
    nodeIds.windowed(2).forEach { (id1, id2) ->
      adjacencyList.getOrPut(id1.id_().asString()) { mutableListOf() }
      adjacencyList.getOrPut(id2.id_().asString()) { mutableListOf() }
      adjacencyList[id1.id_().asString()]?.add(id2.text)
    }
    super.visitEdgeRHS(ctx)
  }

  private fun Id_Context.asString(): String {
    return this.ID()?.text ?: this.STRING().text.removeSurrounding("\"")
  }
}

internal data class SchemaParseResult(
  val graphId: String?,
  val customSchemaFileName: String?,
  val customTargetsFileName: String?,
  val customPackage: String?,
  val adjacencyList: AdjacencyList,
)

internal sealed class Node {
  abstract val id: String

  sealed class Flow : Node() {
    abstract val resultType: String

    data class Local(
      override val id: String,
      override val resultType: String,
    ) : Flow()

    data class Imported(
      override val id: String,
      override val resultType: String,
    ) : Flow()
  }
  data class Screen(override val id: String) : Node()
  data class Parallel(override val id: String) : Node()
}

private const val ATTR_NAME_NODE_TYPE = "type"
private const val ATTR_VALUE_NODE_TYPE_FLOW = "flow"
private const val ATTR_VALUE_NODE_TYPE_SCHEMA = "schema"

private const val ATTR_NAME_FLOW_RESULT_TYPE = "resultType"
