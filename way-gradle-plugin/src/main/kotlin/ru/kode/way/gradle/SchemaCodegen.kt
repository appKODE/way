package ru.kode.way.gradle

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.TypeSpec

internal fun buildSchemaFileSpec(parseResult: SchemaParseResult, config: CodeGenConfig): FileSpec {
  val schemaClassName = schemaClassName(parseResult, config)
  val schemaFileSpec = FileSpec.builder(
    parseResult.customPackage ?: config.outputPackageName,
    parseResult.customSchemaFileName ?: schemaClassName
  )
  val regionRoots = buildRegionRoots(parseResult.adjacencyList)
  val constructorParameters = buildConstructorParameters(parseResult.adjacencyList)
  val constructorProperties = constructorParameters.map {
    PropertySpec.builder(it.name, it.type, KModifier.PRIVATE)
      .initializer(it.name)
      .build()
  }

  val schemaTypeSpec = TypeSpec.classBuilder(name = schemaClassName)
    .apply {
      if (constructorParameters.isNotEmpty()) {
        primaryConstructor(FunSpec.constructorBuilder().addParameters(constructorParameters).build())
        addProperties(constructorProperties)
      }
    }
    .addSuperinterface(libraryClassName("Schema"))
    .addProperty(
      PropertySpec.Companion.builder("regions", LIST.parameterizedBy(libraryClassName("RegionId")), KModifier.OVERRIDE)
        .initializer(
          CodeBlock.builder()
            .add("listOf(")
            .apply {
              regionRoots.forEachIndexed { index, r ->
                add(
                  "%T(%T(%L))",
                  libraryClassName("RegionId"),
                  libraryClassName("Path"),
                  findPathSegmentsEncoded(r, parseResult.adjacencyList)
                )
                if (index != regionRoots.lastIndex) {
                  add(", ")
                }
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
      buildSchemaTargetsSpec(parseResult.adjacencyList)
    )
    .addFunction(
      buildSchemaNodeTypeSpec(parseResult.adjacencyList)
    )
  return schemaFileSpec.addType(schemaTypeSpec.build()).build()
}

private fun buildRegionRoots(adjacencyList: AdjacencyList): List<Node> {
  val regionRoots = mutableListOf<Node>()
  adjacencyList.forEach { (node, children) ->
    if (node is Node.Flow.LocalParallel) {
      regionRoots.addAll(children)
    }
  }
  if (regionRoots.isEmpty()) {
    regionRoots.add(adjacencyList.findRootNode())
  }
  return regionRoots
}

private fun buildConstructorParameters(adjacencyList: AdjacencyList): List<ParameterSpec> {
  val parameters = mutableListOf<ParameterSpec>()
  adjacencyList.forEach { (node, _) ->
    if (node is Node.Flow.Imported) {
      parameters.add(
        ParameterSpec.builder(schemaConstructorPropertyName(node), libraryClassName("Schema"))
          .build()
      )
    }
  }
  return parameters
}

private fun buildSchemaTargetsSpec(adjacencyList: AdjacencyList): FunSpec {
  return FunSpec.builder("target")
    .addModifiers(KModifier.OVERRIDE)
    .addParameter("regionId", libraryClassName("RegionId"))
    .addParameter("segment", libraryClassName("Segment"))
    .returns(libraryClassName("Path").copy(nullable = true))
    .addCode(
      CodeBlock.builder()
        .beginControlFlow("return·when·(regionId)·{")
        .apply {
          buildRegionRoots(adjacencyList).forEachIndexed { regionRootIndex, regionRoot ->
            beginControlFlow("regions[$regionRootIndex] -> {")
            beginControlFlow("when(segment.name) {")
                val importedFlowNodes = mutableListOf<Node>()
                // TODO @AdjacencyMatrix
                //  not very efficient: running DFS and then for each node inspecting all adjacency list to find parent
                //  adjacency matrix would allow to find parent nodes more easily.
                //  This stuff is going on in many places during codegen, search for them if will be optimizing
                dfs(adjacencyList, regionRoot) { node ->
                  when (node) {
                    is Node.Flow.Imported -> {
                      importedFlowNodes.add(node)
                    }
                    is Node.Flow.Local,
                    is Node.Flow.LocalParallel,
                    is Node.Screen -> {
                      addStatement(
                        "%S -> %T(%L)",
                        node.id,
                        libraryClassName("Path"),
                        findPathSegmentsEncoded(node, adjacencyList)
                      )
                    }
                  }
                }
                if (importedFlowNodes.isNotEmpty()) {
                  beginControlFlow("else -> ")
                  importedFlowNodes.forEachIndexed { index, node ->
                    val elvis = if (index > 0) "?: " else ""
                    addStatement(
                      "$elvis%L.target(%L.regions.first(), segment)",
                      schemaConstructorPropertyName(node),
                      schemaConstructorPropertyName(node),
                    )
                    // append prefix unless root node is an imported node too (in which case there's nothing to append)
                    if (node != regionRoot) {
                      addStatement(
                        "?.let { %T(%L).%M(it) }",
                        libraryClassName("Path"),
                        adjacencyList
                          .findAllParents(node, includeThis = true)
                          .reversed()
                          .dropLast(1)
                          .joinToString { "\"${it.id}\"" },
                        libraryMemberName("append"),
                      )
                    }
                  }
                  endControlFlow()
                } else {
                  addStatement("else -> null")
                }
            endControlFlow() // when (segment.name)
            endControlFlow() // regions[index] -> {
          }
        }
        .beginControlFlow("else -> {")
        .addStatement("error(%P)", "unknown regionId=\$regionId")
        .endControlFlow()
        .endControlFlow() // return when
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
          // imported flow nodes must be sorted by largest path length descending, so that when used in "when"
          // startsWith("app.login.main") would be correctly resolved in presence of shorter paths, e.g
          // startsWith("app.login")
          val importedFlowNodes = mutableListOf<Node>()
          adjacencyList.keys.forEach { node ->
            when (node) {
              is Node.Flow.Local -> {
                addStatement(
                  "path == %T(%L) -> %T.NodeType.Flow",
                  libraryClassName("Path"),
                  findPathSegmentsEncoded(node, adjacencyList),
                  libraryClassName("Schema")
                )
              }
              is Node.Flow.Imported -> {
                importedFlowNodes.add(node)
              }
              is Node.Flow.LocalParallel -> {
                addStatement(
                  "path == %T(%L) -> %T.NodeType.Parallel",
                  libraryClassName("Path"),
                  findPathSegmentsEncoded(node, adjacencyList),
                  libraryClassName("Schema")
                )
              }
              is Node.Screen -> {
                addStatement(
                  "path == %T(%L) -> %T.NodeType.Screen",
                  libraryClassName("Path"),
                  findPathSegmentsEncoded(node, adjacencyList),
                  libraryClassName("Schema")
                )
              }
            }
          }
          importedFlowNodes
            .map { it to adjacencyList.findAllParents(it, includeThis = true) }
            .sortedByDescending { (_, parents) -> parents.size }.forEach { (node, parents) ->
              addStatement(
                "path.%M(%T(%L)) -> %L.nodeType(%L.regions.first(), path.%M(%T(%L)))",
                libraryMemberName("startsWith"),
                libraryClassName("Path"),
                parents.reversed().joinToString { "\"${it.id}\"" },
                schemaConstructorPropertyName(node),
                schemaConstructorPropertyName(node),
                libraryMemberName("removePrefix"),
                libraryClassName("Path"),
                parents.reversed().dropLast(1).joinToString { "\"${it.id}\"" },
              )
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

private fun schemaConstructorPropertyName(node: Node) = "${node.id}Schema"

internal fun schemaClassName(parseResult: SchemaParseResult, config: CodeGenConfig): String {
  return parseResult.graphId?.let { "${it}Schema" } ?: config.outputSchemaClassName
}

/**
 * Returns a list of segment names encoded in a string suitable to passing to Path constructor, e.g
 * '"app", "profile", "main"'
 */
private fun findPathSegmentsEncoded(node: Node, adjacencyList: AdjacencyList): String {
  return adjacencyList.findAllParents(node, includeThis = true).reversed().joinToString { "\"${it.id}\"" }
}
