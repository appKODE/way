package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
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
  val rootNode = parseResult.adjacencyList.findRootNode()
  return parseResult.adjacencyList.mapFlow { flow, _ ->
    buildNodeBuilderFileSpec(flow, packageName, parseResult.adjacencyList, isRootNode = rootNode == flow)
  }
}

private fun buildNodeBuilderFileSpec(
  flow: Node.Flow,
  packageName: String,
  adjacencyList: AdjacencyList,
  isRootNode: Boolean
): FileSpec {
  val className = ClassName(packageName, flow.id.toPascalCase() + "NodeBuilder")
  return FileSpec
    .builder(
      packageName,
      className.simpleName
    )
    .addType(buildNodeBuilderTypeSpec(flow, className, adjacencyList, isRootNode))
    .build()
}

internal fun buildNodeBuilderTypeSpec(
  flow: Node.Flow,
  className: ClassName,
  adjacencyList: AdjacencyList,
  isRootNode: Boolean,
): TypeSpec {
  val typeSpecBuilder = TypeSpec.classBuilder(className)
  val constructorBuilder = FunSpec.constructorBuilder()
  val nodeProperties = mutableMapOf<Node, PropertySpec>()
  val lazyBuilderProperties = mutableMapOf<Node.Flow, PropertySpec>()
  val flowNodeParameter = ParameterSpec
    .builder(
      FLOW_NODE_PARAMETER_NAME,
      LambdaTypeName.get(
        receiver = null,
        returnType = libraryClassName("FlowNode").parameterizedBy(STAR, STAR)
      )
    )
    .build()
  constructorBuilder.addParameter(flowNodeParameter)
  val flowNodeProperty = PropertySpec.builder(flowNodeParameter.name, flowNodeParameter.type, KModifier.PRIVATE)
    .initializer(flowNodeParameter.name)
    .build()
  typeSpecBuilder.addProperty(flowNodeProperty)

  dfs(adjacencyList, flow) { node ->
    if (node == flow) return@dfs
    // See NOTE_GROUPING_NODES_BY_FLOW_RULE
    if (node is Node.Screen && adjacencyList.findParentFlow(node) != flow) return@dfs
    if (node is Node.Flow && !isRootNode) return@dfs
    val parameter = when (node) {
      is Node.Flow -> {
        ParameterSpec
          .builder(
            node.id.toCamelCase() + "NodeBuilder",
            LambdaTypeName.get(
              receiver = null,
              returnType = libraryClassName("NodeBuilder")
            )
          )
          .build()
      }
      is Node.Screen -> {
        ParameterSpec
          .builder(
            node.id.toCamelCase() + "Node",
            LambdaTypeName.get(
              receiver = null,
              returnType = libraryClassName("ScreenNode").parameterizedBy(STAR)
            )
          )
          .build()
      }
      is Node.Parallel -> TODO()
    }
    val property = PropertySpec.builder(parameter.name, parameter.type, KModifier.PRIVATE)
      .initializer(parameter.name)
      .build()
    constructorBuilder.addParameter(parameter)
    nodeProperties[node] = property
    when (node) {
      is Node.Flow -> {
        val lazyBuilderProperty = PropertySpec
          .builder("_${parameter.name}", libraryClassName("NodeBuilder"), KModifier.PRIVATE)
          .delegate("lazy(LazyThreadSafetyMode.NONE) { %L() }", parameter.name)
          .build()
        lazyBuilderProperties[node] = lazyBuilderProperty
      }
      is Node.Screen -> Unit
      is Node.Parallel -> TODO()
    }
  }
  val targetsProperty = PropertySpec
    .builder("targets", ClassName(className.packageName, targetsClassName(flow)), KModifier.PRIVATE)
    .initializer(
      "%T(%T(%S))",
      ClassName(className.packageName, targetsClassName(flow)),
      libraryClassName("Path"),
      flow.id
    )
    .build()
  return typeSpecBuilder
    .primaryConstructor(constructorBuilder.build())
    .addProperties(nodeProperties.values)
    .addProperties(lazyBuilderProperties.values)
    .addProperty(targetsProperty)
    .addSuperinterface(libraryClassName("NodeBuilder"))
    .addFunction(
      FunSpec.builder("build")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("path", libraryClassName("Path"))
        .returns(libraryClassName("Node"))
        .addCode(
          createBuildFunctionBody(
            flow,
            adjacencyList,
            targetsProperty,
            lazyBuilderProperties,
            nodeProperties,
            isRootNode
          )
        )
        .build()
    )
    .build()
}

private fun createBuildFunctionBody(
  flow: Node.Flow,
  adjacencyList: Map<Node, List<Node>>,
  targetsProperty: PropertySpec,
  lazyBuilderProperties: Map<Node.Flow, PropertySpec>,
  nodeProperties: Map<Node, PropertySpec>,
  isRootNode: Boolean,
): CodeBlock {
  return CodeBlock.builder()
    .beginControlFlow(
      "check(path.segments.firstOrNull()?.name == %S)",
      flow.id
    )
    .addStatement("%P", "illegal path build requested for \"${flow.id}\" node: \$path")
    .endControlFlow()
    .beginControlFlow("return if (path.segments.size == 1 && path.segments.first().name == %S)", flow.id)
    .addStatement("$FLOW_NODE_PARAMETER_NAME()")
    .endControlFlow() // end "return if"
    .beginControlFlow("else")
    .beginControlFlow("when")
    .apply {
      dfs(adjacencyList, flow) { node ->
        if (node == flow) return@dfs
        // See NOTE_GROUPING_NODES_BY_FLOW_RULE
        if (node is Node.Screen && adjacencyList.findParentFlow(node) != flow) return@dfs
        if (node is Node.Flow && !isRootNode) return@dfs
        when (node) {
          is Node.Flow -> {
            addStatement(
              "path.%M(%N.%L { %T }.path) -> %N.build(path.%M(1))",
              MemberName(LIBRARY_PACKAGE, "startsWith"),
              targetsProperty,
              node.id,
              libraryClassName("Ignore"),
              lazyBuilderProperties[node] ?: error("no lazy builder property for \"${node.id}\""),
              MemberName(LIBRARY_PACKAGE, "drop")
            )
          }
          is Node.Screen -> {
            addStatement(
              "path == %N.%L.path -> %N()",
              targetsProperty,
              node.id,
              nodeProperties[node] ?: error("no builder for screen node \"${node.id}\"")
            )
          }
          is Node.Parallel -> TODO()
        }
      }
      addStatement("else -> error(%P)", "illegal path build requested for \"${flow.id}\" node: \$path")
    }
    .endControlFlow() // end "when"
    .endControlFlow() // end "else"
    .build()
}

private const val FLOW_NODE_PARAMETER_NAME = "flowNode"

// NOTE_GROUPING_NODES_BY_FLOW_RULE
//
// TL;DR: This rule can be formalized as:
//
// 1. All flows in one dot file are registered as the children of the root flow in this file
// 2. Each screen is registered as a child of the nearest flow up the navigation graph
//
// In general, dot-file can contain several flows and several screens belonging to the different flows.
// When specifying edges flows and screens may appear intermixed so it's not clear how to determine node parents.
// There are rules which are used for this.
//
// For example if a single dot file contains the edges specification like this
//
// app_flow -> app_screen1 -> login_flow -> login_screen1 -> login_screen2 -> onboarding_flow -> onboarding_screen1
//
// The parent-child relationship will be derived as follows:
//
// app_flow children are [ login_flow, onboarding_flow, app_screen1 ]
// login_flow children are [ login_screen1, login_screen2 ]
// onboarding_flow children are [ onboarding_screen1 ]
