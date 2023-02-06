package ru.kode.way.gradle

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
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
  // TODO proper regions. NOTE it's likely that there will always be one root node, it will be a ParallelNode,
  val regionRoots = listOf(parseResult.adjacencyList.findRootNode())
  val regions = regionRoots.map { it.id }
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
    // children of a parallel node as region roots
    TODO()
  }
  return FunSpec.builder("target")
    .addModifiers(KModifier.OVERRIDE)
    .addParameter("regionId", libraryClassName("RegionId"))
    .addParameter("segment", libraryClassName("Segment"))
    .returns(libraryClassName("Path"))
    .addCode(
      CodeBlock.builder()
        .beginControlFlow("return·when·(regionId)·{")
        .beginControlFlow("regions[0] -> {")
        .beginControlFlow("when(segment.name) {")
        .apply {
          // TODO @AdjacencyMatrix
          //  not very efficient: running DFS and then for each node inspecting all adjacency list to find parent
          //  adjacency matrix would allow to find parent nodes more easily.
          //  This stuff is going on in many places during codegen, search for them if will be optimizing
          dfs(adjacencyList, regionRoot) { node ->
            addStatement(
              "%S -> %T(%L)",
              node.id,
              libraryClassName("Path"),
              adjacencyList
                .findAllParents(node, includeThis = true).reversed().joinToString(",") { "\"${it.id}\"" }
            )
          }
          addStatement("else -> error(%P)", "unknown segment=\$segment")
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
        .beginControlFlow("when {")
        .apply {
          adjacencyList.keys.forEach { node ->
            when (node) {
              is Node.Flow -> {
                addStatement(
                  "path == %T(%L) -> %T.NodeType.Flow",
                  libraryClassName("Path"),
                  adjacencyList
                    .findAllParents(node, includeThis = true).reversed().joinToString(",") { "\"${it.id}\"" },
                  libraryClassName("Schema")
                )
              }
              is Node.Parallel -> TODO()
              is Node.Screen -> {
                addStatement(
                  "path == %T(%L) -> %T.NodeType.Screen",
                  libraryClassName("Path"),
                  adjacencyList
                    .findAllParents(node, includeThis = true).reversed().joinToString(",") { "\"${it.id}\"" },
                  libraryClassName("Schema")
                )
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
