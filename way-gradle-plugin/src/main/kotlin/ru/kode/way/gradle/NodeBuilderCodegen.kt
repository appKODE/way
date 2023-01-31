package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec

internal fun buildNodeBuilderFileSpecs(
  parseResult: SchemaParseResult,
  config: CodeGenConfig
): List<FileSpec> {
  val packageName = parseResult.customPackage ?: config.outputPackageName
  return parseResult.adjacencyList.mapFlow { flow, _ ->
    buildNodeBuilderFileSpec(flow, packageName, parseResult.adjacencyList)
  }
}

private fun buildNodeBuilderFileSpec(flow: Node.Flow, packageName: String, adjacencyList: AdjacencyList): FileSpec {
  val className = ClassName(packageName, flow.id.toPascalCase() + "NodeBuilder")
  return FileSpec
    .builder(
      packageName,
      className.simpleName
    )
    .addType(buildNodeBuilderTypeSpec(flow, className, adjacencyList))
    .build()
}

internal fun buildNodeBuilderTypeSpec(flow: Node.Flow, className: ClassName, adjacencyList: AdjacencyList): TypeSpec {
  val typeSpecBuilder = TypeSpec.classBuilder(className)
  val constructorBuilder = FunSpec.constructorBuilder()
  val properties = mutableListOf<PropertySpec>()
  val flowNodeParameter = ParameterSpec
    .builder(
      "flowNode",
      LambdaTypeName.get(
        receiver = null,
        returnType = libraryClassName("FlowNode").parameterizedBy(STAR, STAR)
      )
    )
    .build()
  constructorBuilder.addParameter(flowNodeParameter)
  properties.add(
    PropertySpec.builder(flowNodeParameter.name, flowNodeParameter.type, KModifier.PRIVATE)
      .initializer(flowNodeParameter.name)
      .build()
  )

  dfs(adjacencyList, flow) { node ->
    val parameter = ParameterSpec
      .builder(
        node.id.toCamelCase() + "NodeBuilder",
        LambdaTypeName.get(
          receiver = null,
          returnType = libraryClassName("NodeBuilder")
        )
      )
      .build()
    val property = PropertySpec.builder(parameter.name, parameter.type, KModifier.PRIVATE)
      .initializer(parameter.name)
      .build()
    constructorBuilder.addParameter(parameter)
    properties.add(property)

  }
  return typeSpecBuilder
    .primaryConstructor(constructorBuilder.build())
    .addProperties(properties)
    .build()
}
