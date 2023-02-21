package ru.kode.way.gradle

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName

internal fun buildNodeBuilderFileSpecs(
  parseResult: SchemaParseResult,
  config: CodeGenConfig
): List<FileSpec> {
  val packageName = parseResult.customPackage ?: config.outputPackageName
  val rootNode = parseResult.adjacencyList.findRootNode()
  val schemaClassName = ClassName(packageName, schemaClassName(parseResult, config))
  return parseResult.adjacencyList.mapFlow { flow, _ ->
    buildNodeBuilderFileSpec(
      flow,
      packageName,
      parseResult.adjacencyList,
      schemaClassName,
      isRootNode = rootNode == flow
    )
  }
}

private fun buildNodeBuilderFileSpec(
  flow: Node.Flow,
  packageName: String,
  adjacencyList: AdjacencyList,
  schemaClassName: ClassName,
  isRootNode: Boolean
): FileSpec {
  val className = ClassName(packageName, flow.id.toPascalCase() + "NodeBuilder")
  return FileSpec
    .builder(
      packageName,
      className.simpleName
    )
    .addType(buildNodeBuilderTypeSpec(flow, className, schemaClassName, adjacencyList, isRootNode))
    .build()
}

internal fun buildNodeBuilderTypeSpec(
  flow: Node.Flow,
  className: ClassName,
  schemaClassName: ClassName,
  adjacencyList: AdjacencyList,
  isRootNode: Boolean,
): TypeSpec {
  val typeSpecBuilder = TypeSpec.classBuilder(className)
  val constructorBuilder = FunSpec.constructorBuilder()
  val nodeBuilders = mutableMapOf<Node, FunSpec>()
  val lazyBuilderProperties = mutableMapOf<Node, PropertySpec>()

  val factoryBuilderTypeName = className.nestedClass("Factory")
  val factoryTypeSpecBuilder = TypeSpec.interfaceBuilder(factoryBuilderTypeName)
    .addFunction(
      FunSpec.builder(NODE_FACTORY_FLOW_NODE_BUILDER_NAME)
        .addModifiers(KModifier.ABSTRACT)
        .returns(libraryClassName("FlowNode").parameterizedBy(STAR))
        .apply {
          if (flow.parameter != null) {
            addParameter(flow.parameter!!.name, ClassName.bestGuess(flow.parameter!!.type))
          }
        }
        .build()
    )

  val factoryParameter = ParameterSpec
    .builder(
      NODE_FACTORY_PARAMETER_NAME,
      factoryBuilderTypeName
    )
    .build()
  val factoryProperty = PropertySpec.builder(factoryParameter.name, factoryParameter.type, KModifier.PRIVATE)
    .initializer(factoryParameter.name)
    .build()
  constructorBuilder.addParameter(factoryParameter)
  typeSpecBuilder.addProperty(factoryProperty)

  val schemaParameter = ParameterSpec
    .builder(
      SCHEMA_PARAMETER_NAME,
      schemaClassName
    )
    .build()
  val schemaProperty = PropertySpec.builder(schemaParameter.name, schemaParameter.type, KModifier.PRIVATE)
    .initializer(schemaParameter.name)
    .build()
  constructorBuilder.addParameter(schemaParameter)
  typeSpecBuilder.addProperty(schemaProperty)

  dfs(adjacencyList, flow) { node ->
    if (node == flow) return@dfs
    // See NOTE_GROUPING_NODES_BY_FLOW_RULE
    if (node is Node.Screen && adjacencyList.findParentFlow(node) != flow) return@dfs
    if (node is Node.Flow && !isRootNode) return@dfs
    when (node) {
      is Node.Flow -> {
        val flowFactoryName = "create${node.id.toPascalCase()}NodeBuilder"
        factoryTypeSpecBuilder.addFunction(
          FunSpec.builder(flowFactoryName)
            .addModifiers(KModifier.ABSTRACT)
            .returns(libraryClassName("NodeBuilder"))
            .build()
        )
        val lazyPropertyName = "_${node.id.toCamelCase()}NodeBuilder"
        val lazyBuilderProperty = PropertySpec
          .builder(lazyPropertyName, libraryClassName("NodeBuilder"), KModifier.PRIVATE)
          .delegate("lazy(LazyThreadSafetyMode.NONE) { %N.%L() }", factoryParameter, flowFactoryName)
          .build()
        lazyBuilderProperties[node] = lazyBuilderProperty
      }
      is Node.Screen -> {
        val screenBuilderFunSpec = FunSpec.builder("create${node.id.toPascalCase()}Node")
          .addModifiers(KModifier.ABSTRACT)
          .returns(libraryClassName("ScreenNode"))
          .apply {
            if (node.parameter != null) {
              addParameter(node.parameter.name, ClassName.bestGuess(node.parameter.type))
            }
          }
          .build()
        factoryTypeSpecBuilder.addFunction(screenBuilderFunSpec)
        nodeBuilders[node] = screenBuilderFunSpec
      }
      is Node.Parallel -> TODO()
    }
  }
  return typeSpecBuilder
    .primaryConstructor(constructorBuilder.build())
    .addProperties(lazyBuilderProperties.values)
    .addType(factoryTypeSpecBuilder.build())
    .addSuperinterface(libraryClassName("NodeBuilder"))
    .addFunction(
      FunSpec.builder("build")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("path", libraryClassName("Path"))
        .addParameter("payloads", MAP.parameterizedBy(libraryClassName("Path"), ANY))
        .returns(libraryClassName("Node"))
        .addCode(
          createBuildFunctionBody(
            flow,
            adjacencyList,
            lazyBuilderProperties,
            nodeBuilders,
            isRootNode
          )
        )
        .build()
    )
    .addFunction(buildGetTargetFunSpec())
    .addFunction(buildGetPayloadFunSpec())
    .build()
}

private fun createBuildFunctionBody(
  flow: Node,
  adjacencyList: Map<Node, List<Node>>,
  lazyBuilderProperties: Map<Node, PropertySpec>,
  nodeBuilders: Map<Node, FunSpec>,
  isRootNode: Boolean,
): CodeBlock {
  return CodeBlock.builder()
    .beginControlFlow(
      "check(path.segments.firstOrNull()?.name == %S)",
      flow.id
    )
    .addStatement("%P", "illegal path build requested for \"${flow.id}\" node: \$path")
    .endControlFlow()
    .beginControlFlow("return when")
    .apply {
      dfs(adjacencyList, flow) { node ->
        // See NOTE_GROUPING_NODES_BY_FLOW_RULE
        if (node is Node.Screen && adjacencyList.findParentFlow(node) != flow) return@dfs
        if (node is Node.Flow && !isRootNode) return@dfs
        when (node) {
          is Node.Flow -> {
            if (node == flow) {
              if (node.parameter != null) {
                addStatement(
                  "path == %L(%S) -> %L.%L(%L(%S, payloads))",
                  GET_TARGET_FUN_NAME,
                  node.id,
                  NODE_FACTORY_PARAMETER_NAME,
                  NODE_FACTORY_FLOW_NODE_BUILDER_NAME,
                  GET_PAYLOAD_FUN_NAME,
                  node.id
                )
              } else {
                addStatement(
                  "path == %L(%S) -> %L.%L()",
                  GET_TARGET_FUN_NAME,
                  node.id,
                  NODE_FACTORY_PARAMETER_NAME,
                  NODE_FACTORY_FLOW_NODE_BUILDER_NAME
                )
              }
            } else {
              beginControlFlow(
                "path.%M(%L(%S)) ->",
                MemberName(LIBRARY_PACKAGE, "startsWith"),
                GET_TARGET_FUN_NAME,
                node.id,
              )
              addStatement("val targetPath = %L(%S)", GET_TARGET_FUN_NAME, node.id)
              addStatement(
                "%N.build(path.%M(targetPath.length·-·1)," +
                  " payloads·=·payloads.mapKeys·{·it.key.%M(targetPath.length·-·1)·})",
                lazyBuilderProperties[node] ?: error("no lazy builder property for \"${node.id}\""),
                MemberName(LIBRARY_PACKAGE, "drop"),
                MemberName(LIBRARY_PACKAGE, "drop"),
              )
              endControlFlow()
            }
          }
          is Node.Screen -> {
            if (node.parameter != null) {
              addStatement(
                "path == %L(%S) -> %L.%N(%L(%S, payloads))",
                GET_TARGET_FUN_NAME,
                node.id,
                NODE_FACTORY_PARAMETER_NAME,
                nodeBuilders[node] ?: error("no builder for screen node \"${node.id}\""),
                GET_PAYLOAD_FUN_NAME,
                node.id
              )
            } else {
              addStatement(
                "path == %L(%S) -> %L.%N()",
                GET_TARGET_FUN_NAME,
                node.id,
                NODE_FACTORY_PARAMETER_NAME,
                nodeBuilders[node] ?: error("no builder for screen node \"${node.id}\"")
              )
            }
          }
          is Node.Parallel -> TODO()
        }
      }
      addStatement("else -> error(%P)", "illegal path build requested for \"${flow.id}\" node: \$path")
    }
    .endControlFlow() // end "when"
    .build()
}

private fun buildGetTargetFunSpec(): FunSpec {
  return FunSpec.builder(GET_TARGET_FUN_NAME)
    .returns(libraryClassName("Path"))
    .addParameter("segmentName", STRING)
    .addCode(
      "return %L.target(%L.regions.first(),$NBSP%T(segmentName)) ?: error(%P)",
      SCHEMA_PARAMETER_NAME,
      SCHEMA_PARAMETER_NAME,
      libraryClassName("Segment"),
      "internal error: no target generated for segment \"\$segmentName\""
    )
    .build()
}

private fun buildGetPayloadFunSpec(): FunSpec {
  return FunSpec.builder(GET_PAYLOAD_FUN_NAME)
    .addTypeVariable(TypeVariableName("T"))
    .returns(TypeVariableName("T"))
    .addAnnotation(
      AnnotationSpec.builder(Suppress::class)
        .addMember("%S", "UNCHECKED_CAST")
        .build()
    )
    .addParameter("segmentName", STRING)
    .addParameter("payloads", MAP.parameterizedBy(libraryClassName("Path"), ANY))
    .addCode(
      CodeBlock.builder()
        .addStatement("val targetPath = $GET_TARGET_FUN_NAME(segmentName)")
        .addStatement(
          "val payload = payloads[targetPath] ?: error(%P)",
          "no payload for \"\$targetPath\""
        )
        .addStatement("return payload as T")
        .build()
    )
    .build()
}

private const val NODE_FACTORY_FLOW_NODE_BUILDER_NAME = "createFlowNode"
private const val NODE_FACTORY_PARAMETER_NAME = "nodeFactory"
private const val SCHEMA_PARAMETER_NAME = "schema"
private const val GET_TARGET_FUN_NAME = "targetOrError"
private const val GET_PAYLOAD_FUN_NAME = "payloadOrError"

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
