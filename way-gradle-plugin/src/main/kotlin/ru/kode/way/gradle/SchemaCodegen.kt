package ru.kode.way.gradle

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

internal fun buildSchemaFileSpec(parseResult: SchemaParseResult, config: CodeGenConfig): FileSpec {
  val schemaClassName = parseResult.graphId?.let { "${it}Schema" } ?: config.outputSchemaClassName
  val schemaFileSpec = FileSpec.builder(
    parseResult.customPackage ?: config.outputPackageName,
    parseResult.customSchemaFileName ?: schemaClassName
  )
  val regions = listOf("app")
  val schemaTypeSpec = TypeSpec.classBuilder(name = schemaClassName)
    .addSuperinterface(libraryClassName("Schema"))
    .addProperty(
      PropertySpec.Companion.builder("regions", LIST.parameterizedBy(libraryClassName("RegionId")), KModifier.OVERRIDE)
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
      buildSchemaTargetsSpec(regions, parseResult.adjacencyList)
    )
    .addFunction(
      buildSchemaNodeTypeSpec(parseResult.adjacencyList)
    )
  return schemaFileSpec.addType(schemaTypeSpec.build()).build()
}

private fun buildSchemaTargetsSpec(regions: List<String>, adjacencyList: AdjacencyList): FunSpec {
  val regionRoot = if (regions.size == 1) {
    adjacencyList.findRootNode()
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
          dfs(adjacencyList, regionRoot) { node ->
            addStatement(
              "%T(%S) to %T(%L),",
              libraryClassName("Segment"),
              node.id,
              libraryClassName("Path"),
              adjacencyList
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

private fun buildSchemaNodeTypeSpec(adjacencyList: AdjacencyList): FunSpec {
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
          adjacencyList.keys.forEach { node ->
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
