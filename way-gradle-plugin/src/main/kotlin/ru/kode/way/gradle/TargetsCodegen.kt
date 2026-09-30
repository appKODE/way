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
              // An imported schema reachable from several parents gets one target per parent: `<id>Via<Parent>`.
              val chains = adjacencyList.parentChains(targetNode)
              chains.forEach { chain ->
                val parent = chain[chain.lastIndex - 1]
                addTarget(
                  FLOW_TARGET,
                  siblingScreenSchemaKdoc(node, targetNode, parent, adjacencyList),
                  chain.dropWhile { it != node }.drop(1),
                  buildSegmentId,
                  name = if (chains.size > 1) targetNode.id + "Via" + parent.id.toPascalCase() else targetNode.id,
                )
              }
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

/**
 * Returns a KDoc string when [targetNode] is a direct child of [node] (no screen intermediary)
 * AND [node] also has screen siblings at the same level — the pattern that causes accidental screen
 * dismissal. Returns null when [targetNode] is reached through a screen (intended stacking).
 */
private fun siblingScreenSchemaKdoc(
  node: Node.Flow,
  targetNode: Node.Flow,
  directParent: Node,
  adjacencyList: AdjacencyList,
): String? {
  if (targetNode !is Node.Flow.Imported) return null // only flag imported schemas, not local flows
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
 * Emits target accessors for the last node of [pathNodes]:
 * - a short one: a property when the target has no parameter, otherwise a function taking the target's own
 *   parameter. Parameterized ancestors keep the payloads they were built with, so it is meant for navigating
 *   inside a flow which is alive; if such an ancestor is not alive, the event is dropped.
 * - when some ancestor on the path has a parameter, also a full function taking the parameters of every
 *   parameterized ancestor (in path order) followed by the target's own. Ancestor values go into
 *   `ancestorPayloads` so the runtime can rebuild an ancestor that is no longer alive. An ancestor parameter
 *   whose name clashes with another one is renamed to `<nodeId><ParameterName>`.
 */
private fun TypeSpec.Builder.addTarget(
  targetType: ClassName,
  kdoc: String?,
  pathNodes: List<Node>,
  buildSegmentId: (Node) -> String,
  name: String = pathNodes.last().id,
): TypeSpec.Builder {
  val targetNode = pathNodes.last()
  val path = buildPathConstructorCall(nodes = pathNodes, buildSegmentId = buildSegmentId)
  val ancestors = pathNodes.dropLast(1).mapNotNull { n -> n.parameter?.let { n to it } }
  val own = targetNode.parameter
  val shortKdoc = if (ancestors.isEmpty()) kdoc else listOfNotNull(SHORT_TARGET_KDOC, kdoc).joinToString("\n\n")
  if (own == null) {
    addProperty(
      PropertySpec.builder(name, targetType)
        .apply { if (shortKdoc != null) addKdoc(shortKdoc) }
        .initializer("%T(flowPath(%L))", targetType, path)
        .build(),
    )
  } else {
    addFunction(
      FunSpec.builder(name)
        .apply { if (shortKdoc != null) addKdoc(shortKdoc) }
        .addParameter(own.name, parseTypeName(own.type))
        .returns(targetType)
        .addCode(
          CodeBlock.builder().add("return %T(flowPath(%L)", targetType, path).addOwnPayload(own).add(")").build(),
        )
        .build(),
    )
  }
  if (ancestors.isEmpty()) return this
  val allNames = (ancestors.map { it.second } + listOfNotNull(own)).map { it.name }
  val ancestorArgs = ancestors.map { (n, p) ->
    val name = if (allNames.count { it == p.name } > 1) n.id + p.name.replaceFirstChar { it.uppercase() } else p.name
    Triple(n, name, p.type)
  }
  val finalNames = ancestorArgs.map { it.second } + listOfNotNull(own?.name)
  check(finalNames.size == finalNames.toSet().size) {
    "target \"$name\" has clashing parameter names $finalNames after prefixing ancestor parameters " +
      "with their node ids; rename a parameter in the .dot file"
  }
  val code = CodeBlock.builder().add("return %T(flowPath(%L)", targetType, path)
  if (own != null) code.addOwnPayload(own)
  code.add(", ancestorPayloads = mapOf(")
  ancestorArgs.forEachIndexed { i, (n, name, _) ->
    if (i > 0) code.add(", ")
    code.add("%T(%S) to %N", SEGMENT, buildSegmentId(n), name)
  }
  code.add("))")
  return addFunction(
    FunSpec.builder(name)
      .addKdoc(listOfNotNull(FULL_TARGET_KDOC, kdoc).joinToString("\n\n"))
      .apply { ancestorArgs.forEach { (_, name, type) -> addParameter(name, parseTypeName(type)) } }
      .apply { if (own != null) addParameter(own.name, parseTypeName(own.type)) }
      .returns(targetType)
      .addCode(code.build())
      .build(),
  )
}

private fun CodeBlock.Builder.addOwnPayload(own: Parameter): CodeBlock.Builder =
  if (parseTypeName(own.type).isNullable) {
    add(", payload = %N ?: %T", own.name, NULL_PAYLOAD)
  } else {
    add(", payload = %N", own.name)
  }

private const val SHORT_TARGET_KDOC =
  "Short target: parameterized ancestors keep their current payloads. Use it inside a flow which is alive; " +
    "if a parameterized ancestor is not alive anymore, the event is dropped."

private const val FULL_TARGET_KDOC =
  "Full target: passes payloads for every parameterized ancestor, so they can be rebuilt. Use it for a cold " +
    "start, a flow's `initial`, an `AbsoluteTarget` or to jump into a branch which is not alive."

internal val Node.parameter: Parameter?
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
