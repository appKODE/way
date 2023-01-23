package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.TypeSpec
import java.io.File

internal fun generate(file: File, outputDirectory: File, config: CodeGenConfig) {
  buildSpecs(file, config).apply {
    schemaFileSpec.writeTo(outputDirectory)
  }
}

internal fun buildSpecs(
  file: File,
  config: CodeGenConfig
): SchemaOutputSpecs {
  val parseResult = parseSchemaDotFile(file = file)
  return SchemaOutputSpecs(
    schemaFileSpec = buildSchemaFileSpec(parseResult, config),
    nodeBuilderSpecs = emptyList()
  )
}

internal fun buildSchemaFileSpec(parseResult: SchemaParseResult, config: CodeGenConfig): FileSpec {
  val schemaClassName = parseResult.graphId?.let { "${it}Schema" } ?: config.outputSchemaClassName
  val schemaFileSpec = FileSpec.builder(
    config.outputPackageName,
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

internal fun buildSchemaNodeTypeSpec(parseResult: SchemaParseResult): FunSpec {
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

internal class SchemaOutputSpecs(
  val schemaFileSpec: FileSpec,
  val nodeBuilderSpecs: List<FileSpec>,
)

internal fun libraryClassName(name: String): ClassName {
  return ClassName(LIBRARY_PACKAGE, name)
}
