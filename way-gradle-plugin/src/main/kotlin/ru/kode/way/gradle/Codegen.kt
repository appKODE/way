package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.TypeSpec
import org.gradle.configurationcache.extensions.capitalized
import java.io.File

internal fun generate(file: File, outputDirectory: File, config: CodeGenConfig) {
  buildSpecs(file, config).apply {
    schemaFileSpec.writeTo(outputDirectory)
    targetsFileSpec.writeTo(outputDirectory)
  }
}

internal fun buildSpecs(
  file: File,
  config: CodeGenConfig
): SchemaOutputSpecs {
  val parseResult = parseSchemaDotFile(file = file)
  return SchemaOutputSpecs(
    schemaFileSpec = buildSchemaFileSpec(parseResult, config),
    targetsFileSpec = buildTargetsFileSpec(parseResult, config),
    nodeBuilderSpecs = emptyList()
  )
}

private fun buildSchemaFileSpec(parseResult: SchemaParseResult, config: CodeGenConfig): FileSpec {
  val schemaClassName = parseResult.graphId?.let { "${it}Schema" } ?: config.outputSchemaClassName
  val schemaFileSpec = FileSpec.builder(
    parseResult.customPackage ?: config.outputPackageName,
    parseResult.customSchemaFileName ?: schemaClassName
  )
  val regions = listOf("app")
  val schemaTypeSpec = TypeSpec
    .classBuilder(name = schemaClassName)
    .addSuperinterface(libraryClassName("Schema"))
    .addProperty(
      PropertySpec.builder("regions", LIST.parameterizedBy(libraryClassName("RegionId")), KModifier.OVERRIDE)
        .initializer(
          CodeBlock.builder()
            .add("listOf(")
            .apply {
              regions.forEachIndexed { index, regionName ->
                add("%T(%T(%S))", libraryClassName("RegionId"), libraryClassName("Path"), regionName)
                if (index != regions.lastIndex) add(",")
              }
            }
            .add(")")
            .build()
        )
        .build()
    )
    .addFunction(
      FunSpec.builder("children")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("regionId", libraryClassName("RegionId"))
        .returns(SET.parameterizedBy(libraryClassName("Segment")))
        .addCode("return emptySet()")
        .build()
    )
    .addFunction(
      FunSpec.builder("children")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("regionId", libraryClassName("RegionId"))
        .addParameter("segment", libraryClassName("Segment"))
        .returns(SET.parameterizedBy(libraryClassName("Segment")))
        .addCode("return emptySet()")
        .build()
    )
    .addFunction(
      buildSchemaTargetsSpec(regions, parseResult)
    )
    .addFunction(
      buildSchemaNodeTypeSpec(parseResult)
    )
  return schemaFileSpec.addType(schemaTypeSpec.build()).build()
}

private fun buildSchemaTargetsSpec(regions: List<String>, parseResult: SchemaParseResult): FunSpec {
  val regionRoot = if (regions.size == 1) {
    parseResult.adjacencyList.findRootNode()
  } else {
    // children of a parallel node a region roots
    TODO()
  }
  return FunSpec.builder("targets")
    .addModifiers(KModifier.OVERRIDE)
    .addParameter("regionId", libraryClassName("RegionId"))
    .returns(MAP.parameterizedBy(libraryClassName("Segment"), libraryClassName("Path")))
    .addCode(
      CodeBlock.builder()
        .beginControlFlow("return when (regionId) {")
        .beginControlFlow("regions[0] -> {")
        .addStatement("mapOf(")
        .apply {
          // TODO @AdjacencyMatrix
          //  not very efficient: running DFS and then for each node inspecting all adjacency list to find parent
          //   adjacency matrix would allow to find parent nodes more easily
          dfs(parseResult.adjacencyList, regionRoot) { node ->
            addStatement(
              "%T(%S) to %T(%L),",
              libraryClassName("Segment"),
              node.id,
              libraryClassName("Path"),
              parseResult.adjacencyList
                .findAllParents(node, includeThis = true).reversed().joinToString(",") { "\"${it.id}\"" }
            )
          }
        }
        .addStatement(")")
        .endControlFlow()
        .beginControlFlow("else -> {")
        .addStatement("error(%P)", "unknown regionId=\$regionId")
        .endControlFlow()
        .endControlFlow()
        .build()
    )
    .build()
}

private fun buildSchemaNodeTypeSpec(parseResult: SchemaParseResult): FunSpec {
  return FunSpec.builder("nodeType")
    .addModifiers(KModifier.OVERRIDE)
    .addParameter("regionId", libraryClassName("RegionId"))
    .addParameter("path", libraryClassName("Path"))
    .returns(libraryClassName("Schema").nestedClass("NodeType"))
    .addCode(
      CodeBlock.builder()
        .beginControlFlow("return when (regionId) {")
        .beginControlFlow("regions[0] -> {")
        .beginControlFlow("when (path.segments.last().name) {")
        .apply {
          parseResult.adjacencyList.keys.forEach { node ->
            when (node) {
              is Node.Flow -> {
                addStatement("%S -> %T.NodeType.Flow", node.id, libraryClassName("Schema"))
              }
              is Node.Parallel -> TODO()
              is Node.Screen -> {
                addStatement("%S -> %T.NodeType.Screen", node.id, libraryClassName("Schema"))
              }
            }
          }
          beginControlFlow("else -> {")
          addStatement("error(%P)", "internal error: no nodeType for path=\$path")
          endControlFlow()
        }
        .endControlFlow()
        .endControlFlow()
        .beginControlFlow("else -> {")
        .addStatement("error(%P)", "unknown regionId=\$regionId")
        .endControlFlow()
        .endControlFlow()
        .build()
    )
    .build()
}

private fun buildTargetsFileSpec(parseResult: SchemaParseResult, config: CodeGenConfig): FileSpec {
  val targetsFileName = parseResult.graphId?.let { "${it}Targets" } ?: DEFAULT_TARGETS_FILE_NAME
  val packageName = parseResult.customPackage ?: config.outputPackageName
  return FileSpec
    .builder(
      packageName,
      parseResult.customTargetsFileName ?: targetsFileName
    )
    .apply {
      parseResult.adjacencyList.forEachFlow { node, _ ->
        addType(buildFlowTargets(node, parseResult.adjacencyList, packageName))
      }
    }
    .apply {
      dfs(parseResult.adjacencyList, parseResult.adjacencyList.findRootNode()) { node ->
        when (node) {
          is Node.Flow -> addProperty(buildTargetExtensionSpec(node, packageName))
          is Node.Screen,
          is Node.Parallel -> Unit
        }
      }
    }
    .build()
}

private fun buildTargetExtensionSpec(node: Node.Flow, packageName: String): PropertySpec {
  val type = ClassName(packageName, targetsClassName(node))
  return PropertySpec.builder(node.id, type)
    .receiver(libraryClassName("Target").nestedClass("Companion"))
    .getter(
      FunSpec.getterBuilder()
        .addCode("return %T()", type)
        .build()
    )
    .build()
}

private fun buildFlowTargets(node: Node.Flow, adjacencyList: AdjacencyList, packageName: String): TypeSpec {
  return TypeSpec.classBuilder(targetsClassName(node))
    .primaryConstructor(
      FunSpec.constructorBuilder()
        .addParameter(
          ParameterSpec.builder("prefix", libraryClassName("Path").copy(nullable = true))
            .defaultValue("null")
            .build()
        )
        .build()
    )
    .addProperty(
      PropertySpec.builder("prefix", libraryClassName("Path").copy(nullable = true), KModifier.PRIVATE)
        .initializer("prefix")
        .build()
    )
    .apply {
      dfs(adjacencyList, node) { targetNode ->
        if (targetNode == node) return@dfs

        when (targetNode) {
          is Node.Flow -> {
            if (adjacencyList.findParentFlow(targetNode) == node) {
              addFunction(buildFlowTargetSpec(node, targetNode))
              addProperty(buildFlowTargetsPropertySpec(targetNode, packageName))
            }
          }
          is Node.Screen -> {
            if (adjacencyList.findParentFlow(targetNode) == node) {
              addProperty(buildScreenTargetSpec(node, targetNode, adjacencyList))
            }
          }
          is Node.Parallel -> TODO()
        }
      }
    }
    .addFunction(
      FunSpec.builder("flowPath")
        .addModifiers(KModifier.PRIVATE)
        .addParameter("path", libraryClassName("Path"))
        .addCode("return prefix?.%M(path) ?: path", libraryMemberName("append"))
        .returns(libraryClassName("Path"))
        .build()
    )
    .build()
}

private fun buildFlowTargetSpec(node: Node.Flow, targetNode: Node.Flow): FunSpec {
  val resultTypeName = ClassName.bestGuess(node.resultType)
  val targetResultTypeName = ClassName.bestGuess(targetNode.resultType)
  return FunSpec.builder(targetNode.id)
    .addParameter(
      ParameterSpec.builder(
        "onFinish",
        LambdaTypeName.get(
          receiver = null,
          ParameterSpec.builder("result", targetResultTypeName).build(),
          returnType = libraryClassName("FlowTransition").parameterizedBy(resultTypeName)
        )
      )
        .build()
    )
    .returns(libraryClassName("FlowTarget").parameterizedBy(targetResultTypeName, resultTypeName))
    .addCode(
      "return %T(flowPath(%T(%S)), onFinish)", libraryClassName("FlowTarget"), libraryClassName("Path"), targetNode.id
    )
    .build()
}

private fun buildFlowTargetsPropertySpec(targetNode: Node.Flow, packageName: String): PropertySpec {
  val type = ClassName(packageName, targetsClassName(targetNode))
  return PropertySpec.builder(targetNode.id, type)
    .initializer("%T(flowPath(%T(%S)))", type, libraryClassName("Path"), targetNode.id)
    .build()
}

private fun buildScreenTargetSpec(
  node: Node.Flow,
  targetNode: Node.Screen,
  adjacencyList: AdjacencyList
): PropertySpec {
  return PropertySpec.builder(targetNode.id, libraryClassName("ScreenTarget"))
    .initializer(
      "%T(flowPath(%T(%L)))",
      libraryClassName("ScreenTarget"),
      libraryClassName("Path"),
      adjacencyList
        .findAllParents(targetNode, includeThis = true)
        .takeWhile { it != node }
        .reversed()
        .joinToString(",") { "\"${it.id}\"" }
    )
    .build()
}

private fun dfs(adjacencyList: AdjacencyList, root: Node, action: (Node) -> Unit) {
  val discovered = ArrayList<Node>(adjacencyList.size)
  val stack = ArrayDeque<Node>(adjacencyList.size)
  stack.add(root)
  while (stack.isNotEmpty()) {
    val v = stack.removeLast()
    if (!discovered.contains(v)) {
      action(v)
      discovered.add(v)
      stack.addAll(adjacencyList[v].orEmpty())
    }
  }
}

private inline fun AdjacencyList.forEachFlow(action: (Node.Flow, List<Node>) -> Unit) {
  this.forEach { (node, adjacent) ->
    if (node is Node.Flow) {
      action(node, adjacent)
    }
  }
}

private fun AdjacencyList.findRootNode(): Node {
  keys.forEach {
    if (findParent(it) == null) return it
  }
  error("internal error: no root node in graph")
}

private fun AdjacencyList.findParent(node: Node): Node? {
  entries.forEach { (n, adjacent) ->
    if (n == node) return@forEach // continue
    if (adjacent.contains(node)) {
      return n
    }
  }
  return null
}

/**
 * Finds all parents and returns them in the closest-to-farthest order, i.e. for
 * app -> screen1 -> screen2 -> screen3
 *
 * findAllParents(screen3) => [screen2, screen1, app]
 */
private fun AdjacencyList.findAllParents(node: Node, includeThis: Boolean = false): List<Node> {
  val parents = ArrayList<Node>(this.size)
  if (includeThis) {
    parents.add(node)
  }
  var next: Node? = findParent(node)
  while (next != null) {
    parents.add(next)
    next = findParent(next)
  }
  return parents
}

private fun AdjacencyList.findParentFlow(node: Node): Node {
  var next: Node? = findParent(node)
  while (next != null) {
    if (next is Node.Flow) return next
    next = findParent(next)
  }
  error("internal error: screen node $node has no parent flow")
}

internal class SchemaOutputSpecs(
  val schemaFileSpec: FileSpec,
  val targetsFileSpec: FileSpec,
  val nodeBuilderSpecs: List<FileSpec>,
)

private fun targetsClassName(node: Node.Flow): String {
  return node.id.toCamelCase() + "Targets"
}

internal fun libraryClassName(name: String): ClassName {
  return ClassName(LIBRARY_PACKAGE, name)
}

internal fun libraryMemberName(name: String): MemberName {
  return MemberName(LIBRARY_PACKAGE, name)
}

private fun String.toCamelCase(): String {
  return this.capitalized()
}
