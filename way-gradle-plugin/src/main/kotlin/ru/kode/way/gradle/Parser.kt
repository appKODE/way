package ru.kode.way.gradle

import com.squareup.kotlinpoet.UNIT
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import ru.kode.way.gradle.DotParser.GraphContext
import ru.kode.way.gradle.DotParser.Id_Context
import java.io.File
import java.nio.file.Path
import kotlin.io.path.relativeTo

internal fun parseSchemaDotFile(file: File, projectDir: File, warn: (String) -> Unit = {}): SchemaParseResult =
  file.inputStream().use { input ->
    val stream = CommonTokenStream(DotLexer(CharStreams.fromStream(input)))
    val parser = DotParser(stream)
    val parseTree = parser.graph()
    val visitor = Visitor()
    visitor.visitGraph(parseTree)
    visitor.buildResult(file.toPath().relativeTo(projectDir.toPath()), warn)
  }

private class Visitor : DotBaseVisitor<Unit>() {
  private var graphId: String? = null
  private var customSchemaFileName: String? = null
  private var customTargetsFileName: String? = null
  private var customPackage: String? = null

  private val adjacencyList: MutableMap<String, MutableSet<String>> = mutableMapOf()
  private val flowNodes: MutableList<String> = mutableListOf()
  private val parallelNodes: MutableList<String> = mutableListOf()
  private val schemaNodes: MutableList<String> = mutableListOf()

  // History pseudostate nodes: id -> deep flag (`type=deepHistory` => true, `type=history` => false).
  private val historyNodes: MutableMap<String, Boolean> = mutableMapOf()
  private val flowNodeResultTypes: MutableMap<String, String> = mutableMapOf()
  private val nodeParameters: MutableMap<String, Parameter> = mutableMapOf()
  private val declaredNodeTypes: MutableMap<String, String> = mutableMapOf()

  fun buildResult(filePath: Path, warn: (String) -> Unit = {}): SchemaParseResult {
    fun String.toNode(): Node = when {
      flowNodes.contains(this) -> {
        Node.Flow.Local(this, flowNodeResultTypes[this] ?: UNIT.canonicalName, nodeParameters[this])
      }

      schemaNodes.contains(this) -> {
        Node.Flow.Imported(this, flowNodeResultTypes[this] ?: UNIT.canonicalName, nodeParameters[this])
      }

      parallelNodes.contains(this) -> {
        Node.Flow.LocalParallel(this, flowNodeResultTypes[this] ?: UNIT.canonicalName, nodeParameters[this])
      }

      historyNodes.contains(this) -> {
        Node.History(this, deep = historyNodes.getValue(this))
      }

      else -> Node.Screen(this, nodeParameters[this])
    }
    val builtAdjacencyList = this.adjacencyList.entries.associate { (nodeId, adjacentIds) ->
      nodeId.toNode() to adjacentIds.map { it.toNode() }
    }
    validateSchema(builtAdjacencyList, warn)
    if (graphId != null && !validKotlinIdentifier.matches(graphId!!)) {
      error(
        "invalid graph id \"$graphId\": graph ids are used as Kotlin class name prefixes and must be valid " +
          "Kotlin identifiers (start with a letter or underscore, contain only letters, digits, or underscores). " +
          "Quoted DOT graph names with spaces, hyphens, or leading digits are not supported.",
      )
    }
    return SchemaParseResult(
      filePath = filePath,
      adjacencyList = builtAdjacencyList,
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
    for (stmt in ctx.stmt_list().stmt()) {
      // A graph attribute statement is `attrName = attrValue`: id_(0) is the name, id_(1) the value.
      val attrName = stmt.id_(0)?.asString()
      if (attrName == name) {
        return stmt.id_(1)?.asString() ?: error("no value for graph attr '$name'")
      }
    }
    return null
  }

  override fun visitNode_stmt(ctx: DotParser.Node_stmtContext) {
    super.visitNode_stmt(ctx)
    val nodeId = ctx.node_id()?.id_()?.asString() ?: error("no node id for ${ctx.text}")
    val attrs = ctx.attr_list()?.a_list()?.flatMap { parseAttrPairs(it) }.orEmpty()

    // `type=X` present? / value of attribute `name`, if any.
    fun hasNodeType(typeValue: String): Boolean =
      attrs.any { (id, value) -> id.asString() == ATTR_NAME_NODE_TYPE && value.asString() == typeValue }
    fun attrValue(name: String): String? = attrs.find { (id, _) -> id.asString() == name }?.get(1)?.asString()

    val isFlowNode = hasNodeType(ATTR_VALUE_NODE_TYPE_FLOW)
    val isSchemaNode = hasNodeType(ATTR_VALUE_NODE_TYPE_SCHEMA)
    val isParallelFlowNode = hasNodeType(ATTR_VALUE_NODE_TYPE_PARALLEL_FLOW)
    val isHistoryNode = hasNodeType(ATTR_VALUE_NODE_TYPE_HISTORY)
    val isDeepHistoryNode = hasNodeType(ATTR_VALUE_NODE_TYPE_DEEP_HISTORY)
    val newType = when {
      isFlowNode -> "flow"
      isSchemaNode -> "schema"
      isParallelFlowNode -> "parallelFlow"
      isHistoryNode -> "history"
      isDeepHistoryNode -> "deepHistory"
      else -> null
    }
    recordNodeTypeDeclaration(nodeId, newType)
    // Note the deliberate asymmetry below: the parallel branch defaults an omitted resultType to
    // kotlin.Unit, while the flow/schema branches leave it unset (buildResult supplies the Unit
    // default when reading). Do not "unify" these — it would change the recorded result types.
    if (isFlowNode) {
      adjacencyList.getOrPut(nodeId) { mutableSetOf() }
      flowNodes.add(nodeId)
      attrValue(ATTR_NAME_FLOW_RESULT_TYPE)?.let { flowNodeResultTypes[nodeId] = it }
    } else if (isSchemaNode) {
      adjacencyList.getOrPut(nodeId) { mutableSetOf() }
      schemaNodes.add(nodeId)
      attrValue(ATTR_NAME_FLOW_RESULT_TYPE)?.let { flowNodeResultTypes[nodeId] = it }
    } else if (isParallelFlowNode) {
      adjacencyList.getOrPut(nodeId) { mutableSetOf() }
      parallelNodes.add(nodeId)
      // Capture optional resultType for the parallel-flow itself. Defaults to kotlin.Unit when
      // omitted (the parallel-flow rarely cares about its own typed result).
      flowNodeResultTypes[nodeId] = attrValue(ATTR_NAME_FLOW_RESULT_TYPE) ?: UNIT.canonicalName
    } else if (isHistoryNode || isDeepHistoryNode) {
      // History pseudostate leaf: it is reached only through its parent flow's edge (which does the
      // `getOrPut`), never has children of its own, and is NEVER built at runtime — it exists purely
      // so the codegen can emit a typed `HistoryTarget` accessor pointing at the parent flow.
      historyNodes[nodeId] = isDeepHistoryNode
    }

    val parameterType = attrValue(ATTR_NAME_PARAMETER_TYPE)
    val parameterName = attrValue(ATTR_NAME_PARAMETER_NAME)
    if ((parameterName == null) != (parameterType == null)) {
      error("node \"$nodeId\": parameterName and parameterType must both be specified or both omitted")
    }
    if (parameterName != null && parameterType != null) {
      nodeParameters[nodeId] = Parameter(name = parameterName, type = parameterType)
    }
  }

  /**
   * Pairs each attribute id with its value id within one `a_list`. Tolerates a malformed `name "value"`
   * attribute with NO `=` sign by pairing the two adjacent ids positionally — real consumer graphs
   * contain this (prsv main_flow.dot's `parameterName "categoryType"`), and master's positional
   * `chunked(2)` parser accepted it. The stricter `=` form is still preferred and used everywhere else.
   */
  private fun parseAttrPairs(aList: DotParser.A_listContext): List<List<Id_Context>> {
    val pairs = mutableListOf<List<Id_Context>>()
    val children = aList.children ?: return pairs
    var i = 0
    while (i < children.size) {
      val child = children[i]
      if (child is Id_Context) {
        val next = children.getOrNull(i + 1)
        val valueChild = children.getOrNull(i + 2)
        if (next?.text == "=" && valueChild is Id_Context) {
          pairs.add(listOf(child, valueChild))
          i += 3
        } else if (next is Id_Context) {
          pairs.add(listOf(child, next))
          i += 2
        } else {
          i++ // skip genuinely value-less attribute
        }
      } else {
        i++ // skip ',' and '=' terminals
      }
    }
    return pairs
  }

  /** Records [nodeId]'s declared [newType] (no-op when untyped), erroring on a conflicting or duplicate declaration. */
  private fun recordNodeTypeDeclaration(nodeId: String, newType: String?) {
    if (newType == null) return
    val prev = declaredNodeTypes[nodeId]
    if (prev != null) {
      val explanation = if (prev == newType) {
        "redeclared as \"$newType\". Later declarations silently overwrite parameter and " +
          "result-type attributes; remove the duplicate."
      } else {
        "is declared as both \"$prev\" and \"$newType\". Each node id may have at most one type attribute."
      }
      error("duplicate node definition: \"$nodeId\" $explanation")
    }
    declaredNodeTypes[nodeId] = newType
  }

  override fun visitEdge_stmt(ctx: DotParser.Edge_stmtContext) {
    val nodeId = ctx.node_id().id_().asString()
    super.visitEdge_stmt(ctx)
    val rhsFirstNode = ctx.edgeRHS().node_id(0).id_().asString()
    addEdge(from = nodeId, to = rhsFirstNode)
  }

  override fun visitEdgeRHS(ctx: DotParser.EdgeRHSContext) {
    ctx.node_id().windowed(2).forEach { (id1, id2) ->
      addEdge(from = id1.id_().asString(), to = id2.id_().asString())
    }
    super.visitEdgeRHS(ctx)
  }

  /** Ensures both endpoints exist in the adjacency list and records the directed edge [from] → [to]. */
  private fun addEdge(from: String, to: String) {
    adjacencyList.getOrPut(from) { mutableSetOf() }
    adjacencyList.getOrPut(to) { mutableSetOf() }
    adjacencyList[from]?.add(to)
  }

  private fun Id_Context.asString(): String = when {
    ID() != null -> ID()!!.text
    STRING() != null -> STRING()!!.text.removeSurrounding("\"")
    HTML_STRING() != null -> HTML_STRING()!!.text.removeSurrounding("<", ">")
    NUMBER() != null -> NUMBER()!!.text
    else -> error("unrecognized id_ token: ${this.text}")
  }
}

internal data class SchemaParseResult(
  /**
   * Schema file path, relative to project directory
   */
  val filePath: Path,
  val graphId: String?,
  val customSchemaFileName: String?,
  val customSchemaClassName: String? = null,
  val customTargetsFileName: String?,
  val customPackage: String?,
  val adjacencyList: AdjacencyList,
)

internal sealed interface Node {
  val id: String

  sealed interface Flow : Node {
    val resultType: String
    val parameter: Parameter?

    data class Local(override val id: String, override val resultType: String, override val parameter: Parameter?) :
      Flow

    data class LocalParallel(
      override val id: String,
      override val resultType: String,
      override val parameter: Parameter?,
    ) : Flow

    data class Imported(override val id: String, override val resultType: String, override val parameter: Parameter?) :
      Flow
  }
  data class Screen(override val id: String, val parameter: Parameter?) : Node

  /**
   * An SCXML history pseudostate leaf declared under exactly one flow via `parentFlow -> historyNode`
   * with `type="history"` (shallow) or `type="deepHistory"` ([deep] = true).
   *
   * A history node is codegen-time-only: it never gets a runtime NodeType, node factory, adjacency,
   * or finish-event entry. Its sole product is a typed [HISTORY_TARGET] accessor whose path points at
   * the PARENT FLOW (the flow whose most-recently-active configuration to restore), not at the history
   * node itself.
   */
  data class History(override val id: String, val deep: Boolean) : Node
}

internal data class Parameter(val name: String, val type: String)

private fun validateSchema(adjacencyList: AdjacencyList, warn: (String) -> Unit = {}) {
  validateNodeIds(adjacencyList)
  validateNoEmptyFlows(adjacencyList)
  validateNoCycles(adjacencyList)
  validateNoFanIn(adjacencyList)
  validateNoDisconnectedSubgraphs(adjacencyList)
  validateHistoryNodes(adjacencyList)
  warnSiblingScreenAndSchema(adjacencyList, warn)
}

private fun validateHistoryNodes(adjacencyList: AdjacencyList) {
  for ((node, children) in adjacencyList) {
    if (node !is Node.History) continue
    if (children.isNotEmpty()) {
      error(
        "history node \"${node.id}\" must be a childless leaf, but has children: " +
          "${children.joinToString { "\"${it.id}\"" }}. A history pseudostate cannot have outgoing edges.",
      )
    }
    val parent = adjacencyList.findParent(node)
      ?: error(
        "history node \"${node.id}\" has no parent flow. Declare it under exactly one flow via an edge, " +
          "e.g. `parentFlow -> ${node.id}`.",
      )
    when (parent) {
      is Node.Flow.Local -> Unit

      // history under a plain flow hosts its accessor in that flow's Targets
      is Node.Flow.LocalParallel ->
        // A parallel-parented history is valid, but its accessor is emitted into the nearest ENCLOSING
        // plain flow's Targets class. A root/top parallel with no enclosing plain flow has nowhere to host it.
        if (adjacencyList.findAllParents(parent, includeThis = false).none { it is Node.Flow.Local }) {
          error(
            "history node \"${node.id}\" is under a root parallel \"${parent.id}\" with no enclosing flow to " +
              "host its accessor; nest the parallel under a flow.",
          )
        }

      else ->
        error(
          "history node \"${node.id}\" must be a direct child of a plain flow (type=flow) or a parallel flow, " +
            "but its parent \"${parent.id}\" is a ${parent::class.simpleName}. History under IMPORTED flows is " +
            "not supported.",
        )
    }
  }
}

private val validKotlinIdentifier = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")

private fun validateNodeIds(adjacencyList: AdjacencyList) {
  for (node in adjacencyList.keys) {
    if (!validKotlinIdentifier.matches(node.id)) {
      error(
        "invalid node id \"${node.id}\": node ids must be valid Kotlin identifiers " +
          "(start with a letter or underscore, contain only letters, digits, or underscores). " +
          "Quoted DOT ids with spaces, hyphens, or leading digits are not supported.",
      )
    }
  }
}

private fun validateNoEmptyFlows(adjacencyList: AdjacencyList) {
  // LocalParallel nodes must always have children regardless of depth — an empty parallel has
  // no regions at runtime and causes a crash, not a graceful error.
  // For Local flows: only flag root nodes (no parent) with no children; non-root childless
  // Local flows are valid (imported sub-schemas or parallel children declared elsewhere).
  // Imported (schema) flows are excluded — they always appear childless here by design.
  val nodesWithIncoming = adjacencyList.values.flatten().toSet()
  for ((node, children) in adjacencyList) {
    if (node is Node.Flow.LocalParallel && children.isEmpty()) {
      error("parallel node \"${node.id}\" has no children; a parallel node must have at least one child flow")
    }
    if (node is Node.Flow.Local && node !in nodesWithIncoming && children.isEmpty()) {
      error("flow node \"${node.id}\" has no children; a flow must have at least one child screen or flow")
    }
  }
}

private fun validateNoFanIn(adjacencyList: AdjacencyList) {
  // Imported schemas (`type=schema`) are intentionally reusable across entry points: the
  // runtime resolves which entry was taken via AbsoluteTarget paths. Only LOCAL nodes
  // (flows, parallels, screens) need a unique parent for backstack ordering.
  val incomingCount = mutableMapOf<Node, Int>()
  adjacencyList.values.flatten().forEach { node ->
    if (node is Node.Flow.Imported) return@forEach
    incomingCount[node] = (incomingCount[node] ?: 0) + 1
  }
  val fanInNodes = incomingCount.filter { it.value > 1 }.keys
  if (fanInNodes.isNotEmpty()) {
    error(
      "invalid schema: the following local nodes have multiple incoming edges (fan-in): " +
        "${fanInNodes.joinToString { "\"${it.id}\"" }}. " +
        "Each local node must have exactly one parent. Restructure the graph to eliminate shared " +
        "nodes (note: `type=schema` imported nodes are exempt from this check).",
    )
  }
}

private fun validateNoCycles(adjacencyList: AdjacencyList) {
  // DFS-based cycle detection: track the current recursion stack separately from globally visited nodes.
  val globalVisited = HashSet<Node>(adjacencyList.size)

  fun dfsCheck(node: Node, currentPath: List<Node>) {
    globalVisited.add(node)
    for (neighbour in adjacencyList[node].orEmpty()) {
      if (currentPath.contains(neighbour)) {
        val cycle = (currentPath + neighbour).joinToString(" -> ") { it.id }
        error("cycle detected in schema: node \"${neighbour.id}\" is reachable from itself via path: $cycle")
      }
      if (!globalVisited.contains(neighbour)) {
        dfsCheck(neighbour, currentPath + neighbour)
      }
    }
  }

  // Find root nodes (nodes with no incoming edges) and start DFS from each.
  val nodesWithIncoming = adjacencyList.values.flatten().toSet()
  val roots = adjacencyList.keys.filter { it !in nodesWithIncoming }
  // If no roots found (fully cyclic graph), start from every node.
  val startNodes = if (roots.isEmpty()) adjacencyList.keys.toList() else roots
  for (root in startNodes) {
    if (!globalVisited.contains(root)) {
      dfsCheck(root, listOf(root))
    }
  }
  // Also catch cycles in components disconnected from any root.
  for (node in adjacencyList.keys) {
    if (!globalVisited.contains(node)) {
      dfsCheck(node, listOf(node))
    }
  }
}

private fun validateNoDisconnectedSubgraphs(adjacencyList: AdjacencyList) {
  // DFS from flow-type roots only. Non-flow nodes (screens, imported schemas) with no incoming
  // edges are orphaned by definition — they should never be treated as valid graph roots.
  val nodesWithIncoming = adjacencyList.values.flatten().toSet()
  val flowRoots = adjacencyList.keys.filter { it is Node.Flow && it !in nodesWithIncoming }
  if (flowRoots.size > 1) {
    error(
      "schema has multiple root flows: ${flowRoots.joinToString { "\"${it.id}\"" }}. " +
        "A schema must have exactly one root flow.",
    )
  }
  val reachable = HashSet<Node>(adjacencyList.size)
  val startNodes = if (flowRoots.isEmpty()) adjacencyList.keys.toList() else flowRoots
  for (root in startNodes) {
    dfs(adjacencyList, root) { reachable.add(it) }
  }
  val unreachable = adjacencyList.keys.filter { it !in reachable }
  if (unreachable.isNotEmpty()) {
    error(
      "disconnected subgraph detected: the following nodes are not reachable from any root and will " +
        "never be navigated to: ${unreachable.joinToString { "\"${it.id}\"" }}. " +
        "Connect them to the graph or remove them.",
    )
  }
}

/**
 * Warns when a [Node.Flow.Local] has both screen nodes and imported-schema (child flow) nodes as
 * direct children — that is, they share the same parent in the DOT graph.
 *
 * This pattern is valid but frequently accidental: navigating to the schema from any screen in the
 * flow will dismiss that screen from the alive stack (alive stack becomes [flow, schema] instead of
 * [flow, screen, schema]). If the intent is to keep a screen alive while entering the child flow,
 * the DOT edge should go from the screen to the schema, not from the flow to the schema.
 *
 * Example of the suspicious pattern:
 *   mainFlow -> main        // screen — sibling of chatFlow
 *   mainFlow -> chatFlow    // schema — main is dismissed when navigating here
 *
 * Example of the corrected pattern:
 *   mainFlow -> main        // screen
 *   main -> chatFlow        // schema — main stays alive in the alive stack
 */
private fun warnSiblingScreenAndSchema(adjacencyList: AdjacencyList, warn: (String) -> Unit) {
  adjacencyList.forEachFlow { flow, children ->
    val screenSiblings = children.filterIsInstance<Node.Screen>()
    val schemaSiblings = children.filterIsInstance<Node.Flow.Imported>()
    if (screenSiblings.isEmpty() || schemaSiblings.isEmpty()) return@forEachFlow
    val screens = screenSiblings.joinToString { "\"${it.id}\"" }
    val schemas = schemaSiblings.joinToString { "\"${it.id}\"" }
    warn(
      "[way] \"${flow.id}\" has screen(s) $screens and child-flow schema(s) $schemas as direct siblings. " +
        "Navigating to $schemas from a screen in \"${flow.id}\" will dismiss that screen — " +
        "alive stack becomes [${flow.id}, schema], not [${flow.id}, screen, schema]. " +
        "If you want the screen to remain alive while inside the schema, change the edge: " +
        "\"${flow.id} -> schema\" → \"screen -> schema\".",
    )
  }
}

private const val ATTR_NAME_NODE_TYPE = "type"
private const val ATTR_VALUE_NODE_TYPE_FLOW = "flow"
private const val ATTR_VALUE_NODE_TYPE_SCHEMA = "schema"
private const val ATTR_VALUE_NODE_TYPE_PARALLEL_FLOW = "parallelFlow"
private const val ATTR_VALUE_NODE_TYPE_HISTORY = "history"
private const val ATTR_VALUE_NODE_TYPE_DEEP_HISTORY = "deepHistory"

private const val ATTR_NAME_FLOW_RESULT_TYPE = "resultType"
private const val ATTR_NAME_PARAMETER_NAME = "parameterName"
private const val ATTR_NAME_PARAMETER_TYPE = "parameterType"
