package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

internal fun buildTargetsFileSpec(parseResult: SchemaParseResult, config: CodeGenConfig): FileSpec {
  val targetsFileName = parseResult.graphId?.let { "${it}Targets" } ?: DEFAULT_TARGETS_FILE_NAME
  val packageName = parseResult.customPackage ?: config.outputPackageName
  val rootNode = parseResult.adjacencyList.findRootNode()
  return FileSpec.builder(
    packageName,
    parseResult.customTargetsFileName ?: targetsFileName
  )
    .apply {
      parseResult.adjacencyList.forEachFlow { node, _ ->
        addType(
          buildFlowTargets(
            node,
            parseResult.adjacencyList,
            isRootNode = node == rootNode,
            buildSegmentId = { buildSegmentId(parseResult.filePath, it) }
          )
        )
      }
    }
    .apply {
      dfs(parseResult.adjacencyList, rootNode) { node ->
        when (node) {
          is Node.Flow.Local -> addProperty(buildTargetExtensionSpec(node, packageName))
          is Node.Flow.Imported,
          is Node.Flow.LocalParallel,
          is Node.Screen -> Unit
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
        .build()
    )
    .build()
}

private fun buildFlowTargets(
  node: Node.Flow,
  adjacencyList: AdjacencyList,
  isRootNode: Boolean,
  buildSegmentId: (Node) -> String
): TypeSpec {
  return TypeSpec.classBuilder(targetsClassName(node))
    .primaryConstructor(
      FunSpec.constructorBuilder()
        .addParameter(
          ParameterSpec.builder("prefix", PATH.copy(nullable = true))
            .defaultValue("null")
            .build()
        )
        .build()
    )
    .addProperty(
      PropertySpec.builder("prefix", PATH.copy(nullable = true), KModifier.PRIVATE)
        .initializer("prefix")
        .build()
    )
    .apply {
      dfs(adjacencyList, node) { targetNode ->
        if (targetNode == node) return@dfs
        // See NOTE_GROUPING_NODES_BY_FLOW_RULE
        if (targetNode is Node.Screen && adjacencyList.findParentFlow(targetNode) != node) return@dfs

        when (targetNode) {
          is Node.Flow -> {
            if (isRootNode) {
              addFunction(buildFlowTargetSpec(node, targetNode, adjacencyList, buildSegmentId))
            }
          }
          is Node.Screen -> {
            if (adjacencyList.findParentFlow(targetNode) == node) {
              if (targetNode.parameter != null) {
                addFunction(
                  buildScreenTargetFunSpec(node, targetNode, adjacencyList, targetNode.parameter, buildSegmentId)
                )
              } else {
                addProperty(buildScreenTargetPropertySpec(node, targetNode, adjacencyList, buildSegmentId))
              }
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
        .build()
    )
    .build()
}

private fun buildFlowTargetSpec(
  node: Node.Flow,
  targetNode: Node.Flow,
  adjacencyList: AdjacencyList,
  buildSegmentId: (Node) -> String
): FunSpec {
  val resultTypeName = ClassName.bestGuess(node.resultType)
  val targetResultTypeName = ClassName.bestGuess(targetNode.resultType)
  val parameter = targetNode.parameter
  return FunSpec.builder(targetNode.id)
    .apply {
      if (parameter != null) {
        addParameter(parameter.name, ClassName.bestGuess(parameter.type))
      }
    }
    .addParameter(
      ParameterSpec.builder(
        "onFinishRequest",
        LambdaTypeName.get(
          receiver = null,
          ParameterSpec.builder("result", targetResultTypeName).build(),
          returnType = FLOW_TRANSITION.parameterizedBy(resultTypeName)
        )
      )
        .build()
    )
    .returns(FLOW_TARGET.parameterizedBy(targetResultTypeName, resultTypeName))
    .addCode(
      "return %T(flowPath(%L), payload = %L, onFinishRequest)",
      FLOW_TARGET,
      buildPathConstructorCall(
        nodes = adjacencyList
          .findAllParents(targetNode, includeThis = true)
          .takeWhile { it != node }
          .reversed(),
        buildSegmentId = buildSegmentId
      ),
      parameter?.name ?: "null"
    )
    .build()
}

private fun buildScreenTargetPropertySpec(
  node: Node.Flow,
  targetNode: Node.Screen,
  adjacencyList: AdjacencyList,
  buildSegmentId: (Node) -> String
): PropertySpec {
  return PropertySpec.builder(targetNode.id, SCREEN_TARGET)
    .initializer(
      "%T(flowPath(%L))",
      SCREEN_TARGET,
      buildPathConstructorCall(
        nodes = adjacencyList
          .findAllParents(targetNode, includeThis = true)
          .takeWhile { it != node }
          .reversed(),
        buildSegmentId = buildSegmentId
      ),
    )
    .build()
}

private fun buildScreenTargetFunSpec(
  node: Node.Flow,
  targetNode: Node.Screen,
  adjacencyList: AdjacencyList,
  parameter: Parameter,
  buildSegmentId: (Node) -> String
): FunSpec {
  return FunSpec.builder(targetNode.id)
    .addParameter(parameter.name, ClassName.bestGuess(parameter.type))
    .returns(SCREEN_TARGET)
    .addCode(
      "return %T(flowPath(%L), payload = %L)",
      SCREEN_TARGET,
      buildPathConstructorCall(
        nodes = adjacencyList
          .findAllParents(targetNode, includeThis = true)
          .takeWhile { it != node }
          .reversed(),
        buildSegmentId = buildSegmentId
      ),
      parameter.name,
    )
    .build()
}

internal fun targetsClassName(node: Node.Flow): String {
  return node.id.toPascalCase() + "Targets"
}
