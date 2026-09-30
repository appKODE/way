package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

internal fun buildTargetsFileSpec(
  parseResult: SchemaParseResult,
  config: CodeGenConfig,
  registry: SchemaRegistry,
): FileSpec {
  val packageName = parseResult.customPackage ?: config.outputPackageName
  val rootNode = parseResult.adjacencyList.findRootNode()
  return FileSpec.builder(
    packageName,
    targetsFileName(parseResult),
  )
    .apply {
      parseResult.adjacencyList.forEachFlow { node, _ ->
        addType(
          buildFlowTargets(
            node,
            parseResult.adjacencyList,
            isRootNode = node == rootNode,
            buildSegmentId = { buildSegmentId(it, parseResult, registry) },
          ),
        )
      }
    }
    .apply {
      dfs(parseResult.adjacencyList, rootNode) { node ->
        when (node) {
          is Node.Flow.Local -> addProperty(buildTargetExtensionSpec(node, packageName))

          is Node.Flow.Imported,
          is Node.Flow.LocalParallel,
          is Node.Screen,
          is Node.History,
          -> Unit
        }
      }
    }
    .build()
}

private fun buildTargetExtensionSpec(node: Node.Flow, packageName: String): PropertySpec {
  val type = ClassName(packageName, targetsClassName(node))
  return PropertySpec.builder(node.id, type)
    .receiver(TARGET.nestedClass("Companion"))
    .getter(
      FunSpec.getterBuilder()
        .addCode("return %T()", type)
        .build(),
    )
    .build()
}

private fun buildFlowTargets(
  node: Node.Flow,
  adjacencyList: AdjacencyList,
  isRootNode: Boolean,
  buildSegmentId: (Node) -> String,
): TypeSpec {
  return TypeSpec.classBuilder(targetsClassName(node))
    .primaryConstructor(
      FunSpec.constructorBuilder()
        .addParameter(
          ParameterSpec.builder("prefix", PATH.copy(nullable = true))
            .defaultValue("null")
            .build(),
        )
        .build(),
    )
    .addProperty(
      PropertySpec.builder("prefix", PATH.copy(nullable = true), KModifier.PRIVATE)
        .initializer("prefix")
        .build(),
    )
    .apply {
      dfs(adjacencyList, node) { targetNode ->
        if (targetNode == node) return@dfs
        // See NOTE_GROUPING_NODES_BY_FLOW_RULE
        if (targetNode is Node.Screen && adjacencyList.findParentFlow(targetNode) != node) return@dfs

        when (targetNode) {
          is Node.Flow -> {
            if (isRootNode) {
              addFlowTarget(node, targetNode, adjacencyList, buildSegmentId)
            }
          }

          is Node.Screen -> {
            if (adjacencyList.findParentFlow(targetNode) == node) {
              addTarget(SCREEN_TARGET, null, pathNodesBetween(node, targetNode, adjacencyList), buildSegmentId)
            }
          }

          is Node.History -> {
            // A history child emits its accessor in the nearest ENCLOSING LOCAL FLOW's Targets class.
            // For a flow-parented history this IS its parent (old behavior); for a parallel-parented
            // history it routes into the enclosing plain flow. Emitted in exactly one class.
            val hostFlow = adjacencyList
              .findAllParents(targetNode, includeThis = false)
              .firstOrNull { it is Node.Flow.Local }
            if (hostFlow == node) {
              addProperty(buildHistoryTargetPropertySpec(node, targetNode, adjacencyList, buildSegmentId))
            }
          }
        }
      }
    }
    .addFunction(
      FunSpec.builder("flowPath")
        .addModifiers(KModifier.PRIVATE)
        .addParameter("path", PATH)
        .addCode("return prefix?.%M(path) ?: path", libraryMemberName("append"))
        .returns(PATH)
        .build(),
    )
    .build()
}

private fun TypeSpec.Builder.addFlowTarget(
  node: Node.Flow,
  targetNode: Node.Flow,
  adjacencyList: AdjacencyList,
  buildSegmentId: (Node) -> String,
): TypeSpec.Builder {
  val kdoc = siblingScreenSchemaKdoc(node, targetNode, adjacencyList)
  return addTarget(FLOW_TARGET, kdoc, pathNodesBetween(node, targetNode, adjacencyList), buildSegmentId)
}

/**
 * Returns a KDoc string when [targetNode] is a direct child of [node] (no screen intermediary)
 * AND [node] also has screen siblings at the same level — the pattern that causes accidental screen
 * dismissal. Returns null when [targetNode] is reached through a screen (intended stacking).
 */
private fun siblingScreenSchemaKdoc(node: Node.Flow, targetNode: Node.Flow, adjacencyList: AdjacencyList): String? {
  if (targetNode !is Node.Flow.Imported) return null // only flag imported schemas, not local flows
  val directParent = adjacencyList.findParent(targetNode) ?: return null
  if (directParent != node) return null // goes through a screen — intended stacking
  val screenSiblings = adjacencyList[node].orEmpty().filterIsInstance<Node.Screen>()
  if (screenSiblings.isEmpty()) return null
  val screens = screenSiblings.joinToString { "`${it.id}`" }
  return "**Alive-stack note:** navigating here dismisses the current screen in `${node.id}` — " +
    "alive stack becomes `[${node.id}, ${targetNode.id}]`, not `[${node.id}, screen, ${targetNode.id}]`. " +
    "Screen sibling(s) in this flow: $screens. " +
    "To keep a screen alive while entering this schema, move the DOT edge: " +
    "`${node.id} -> ${targetNode.id}` → `screen -> ${targetNode.id}`."
}

/**
 * Emits `public val <historyId>: HistoryTarget = HistoryTarget(flowPath(Path(<history parent path>)), deep = <deep>)`.
 *
 * [node] is the enclosing LOCAL FLOW whose Targets class this accessor lives in. The emitted path points at
 * the history node's ACTUAL parent flow or parallel (the node it is declared under) — the flow/parallel whose
 * most-recently active configuration [ru.kode.way.HistoryTarget] restores — NOT the host [node] and NOT the
 * history node's own segment. For a flow-parented history the history parent IS [node], so the path is
 * unchanged; for a parallel-parented history it is the parallel's absolute path.
 */
private fun buildHistoryTargetPropertySpec(
  node: Node.Flow,
  targetNode: Node.History,
  adjacencyList: AdjacencyList,
  buildSegmentId: (Node) -> String,
): PropertySpec {
  val historyParent = adjacencyList.findParent(targetNode) ?: node
  return PropertySpec.builder(targetNode.id, HISTORY_TARGET)
    .initializer(
      "%T(flowPath(%L), deep = %L)",
      HISTORY_TARGET,
      buildPathConstructorCall(reversedParents(historyParent, adjacencyList), buildSegmentId),
      targetNode.deep,
    )
    .build()
}

/**
 * Emits a target accessor for the last node of [pathNodes]. It is a property when no node on the path has a
 * parameter, otherwise a function taking the parameters of every parameterized ancestor on the path (in path
 * order) followed by the target's own. Ancestor values go into `ancestorPayloads` so the runtime can rebuild
 * an ancestor that is no longer alive. An ancestor parameter whose name clashes with another one is renamed
 * to `<nodeId><ParameterName>`.
 */
private fun TypeSpec.Builder.addTarget(
  targetType: ClassName,
  kdoc: String?,
  pathNodes: List<Node>,
  buildSegmentId: (Node) -> String,
): TypeSpec.Builder {
  val targetNode = pathNodes.last()
  val path = buildPathConstructorCall(nodes = pathNodes, buildSegmentId = buildSegmentId)
  val ancestors = pathNodes.dropLast(1).mapNotNull { n -> n.parameter?.let { n to it } }
  val own = targetNode.parameter
  if (ancestors.isEmpty() && own == null) {
    return addProperty(
      PropertySpec.builder(targetNode.id, targetType)
        .apply { if (kdoc != null) addKdoc(kdoc) }
        .initializer("%T(flowPath(%L))", targetType, path)
        .build(),
    )
  }
  val allNames = (ancestors.map { it.second } + listOfNotNull(own)).map { it.name }
  val ancestorArgs = ancestors.map { (n, p) ->
    val name = if (allNames.count { it == p.name } > 1) n.id + p.name.replaceFirstChar { it.uppercase() } else p.name
    Triple(n, name, p.type)
  }
  val finalNames = ancestorArgs.map { it.second } + listOfNotNull(own?.name)
  check(finalNames.size == finalNames.toSet().size) {
    "target \"${targetNode.id}\" has clashing parameter names $finalNames after prefixing ancestor parameters " +
      "with their node ids; rename a parameter in the .dot file"
  }
  val code = CodeBlock.builder().add("return %T(flowPath(%L)", targetType, path)
  if (own != null) {
    if (parseTypeName(own.type).isNullable) {
      code.add(", payload = %L ?: %T", own.name, NULL_PAYLOAD)
    } else {
      code.add(", payload = %L", own.name)
    }
  }
  if (ancestorArgs.isNotEmpty()) {
    code.add(", ancestorPayloads = mapOf(")
    ancestorArgs.forEachIndexed { i, (n, name, _) ->
      if (i > 0) code.add(", ")
      code.add("%T(%S) to %L", SEGMENT, buildSegmentId(n), name)
    }
    code.add(")")
  }
  code.add(")")
  return addFunction(
    FunSpec.builder(targetNode.id)
      .apply { if (kdoc != null) addKdoc(kdoc) }
      .apply { ancestorArgs.forEach { (_, name, type) -> addParameter(name, parseTypeName(type)) } }
      .apply { if (own != null) addParameter(own.name, parseTypeName(own.type)) }
      .returns(targetType)
      .addCode(code.build())
      .build(),
  )
}

private val Node.parameter: Parameter?
  get() = when (this) {
    is Node.Flow -> parameter
    is Node.Screen -> parameter
    is Node.History -> null
  }

/**
 * The chain of nodes from [flow]'s first descendant down to [targetNode] inclusive, in root-to-target
 * order (i.e. [targetNode] and its ancestors up to but excluding [flow]). This is the node list used
 * to build the relative path passed to the generated `flowPath(...)`.
 */
private fun pathNodesBetween(flow: Node.Flow, targetNode: Node, adjacencyList: AdjacencyList): List<Node> =
  adjacencyList.findAllParents(targetNode, includeThis = true).takeWhile { it != flow }.reversed()

internal fun targetsClassName(node: Node.Flow): String = node.id.toPascalCase() + "Targets"
