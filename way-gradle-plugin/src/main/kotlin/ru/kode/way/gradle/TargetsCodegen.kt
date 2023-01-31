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
  return FileSpec.builder(
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

private fun targetsClassName(node: Node.Flow): String {
  return node.id.toCamelCase() + "Targets"
}
