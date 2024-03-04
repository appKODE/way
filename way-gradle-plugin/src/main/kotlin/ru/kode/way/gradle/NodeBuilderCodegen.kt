package ru.kode.way.gradle

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MUTABLE_MAP
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import java.nio.file.Path

internal fun buildNodeBuilderFileSpecs(
  parseResult: SchemaParseResult,
  config: CodeGenConfig
): List<FileSpec> {
  val packageName = parseResult.customPackage ?: config.outputPackageName
  val rootNode = parseResult.adjacencyList.findRootNode()
  val schemaClassName = ClassName(packageName, schemaClassName(parseResult, config))
  return parseResult.adjacencyList.mapFlow { flow, _ ->
    val className = ClassName(packageName, flow.id.toPascalCase() + "NodeBuilder")
    FileSpec
      .builder(
        packageName,
        className.simpleName
      )
      .addType(
        buildNodeBuilderTypeSpec(
          flow = flow,
          className = className,
          schemaClassName = schemaClassName,
          adjacencyList = parseResult.adjacencyList,
          isRootNode = rootNode == flow,
          schemaFilePath = parseResult.filePath
        )
      )
      .build()
  }
}

internal fun buildNodeBuilderTypeSpec(
  flow: Node.Flow,
  className: ClassName,
  schemaClassName: ClassName,
  adjacencyList: AdjacencyList,
  isRootNode: Boolean,
  schemaFilePath: Path,
): TypeSpec {
  fun buildSegmentId(node: Node): String {
    return "${node.id}@$schemaFilePath"
  }

  val typeSpecBuilder = TypeSpec.classBuilder(className)
  val constructorBuilder = FunSpec.constructorBuilder()
  val nodeBuilders = mutableMapOf<Node, FunSpec>()
  val lazyNodeBuilderFactories = mutableMapOf<Node, FunSpec>()

  val factoryBuilderTypeName = className.nestedClass("Factory")
  val factoryTypeSpecBuilder = TypeSpec.interfaceBuilder(factoryBuilderTypeName)
    .addFunction(
      FunSpec.builder(NODE_FACTORY_FLOW_NODE_BUILDER_NAME)
        .addModifiers(KModifier.ABSTRACT)
        .returns(
          when (flow) {
            is Node.Flow.Local -> FLOW_NODE.parameterizedBy(STAR)
            is Node.Flow.LocalParallel -> PARALLEL_NODE
            is Node.Flow.Imported -> error("unexpected node type: ${flow::class.simpleName}")
          }
        )
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
  val schemaProperty = PropertySpec.builder(schemaParameter.name, schemaParameter.type, KModifier.OVERRIDE)
    .initializer(schemaParameter.name)
    .build()
  constructorBuilder.addParameter(schemaParameter)
  typeSpecBuilder.addProperty(schemaProperty)
  val builderCachePropertyName = "nodeBuilders"

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
            .returns(NODE_BUILDER)
            .apply {
              if (node.parameter != null) {
                addParameter(node.parameter!!.name, ClassName.bestGuess(node.parameter!!.type))
              }
            }
            .build()
        )
        val lazyPropertyBuilderFun = FunSpec
          .builder("${node.id.toCamelCase()}NodeBuilder")
          .addModifiers(KModifier.PRIVATE)
          .returns(NODE_BUILDER)
          .apply {
            if (node.parameter != null) {
              addParameter("payloads", MAP.parameterizedBy(PATH, ANY))
            }
          }
          .addParameter("rootSegmentAlias", SEGMENT.copy(nullable = true))
          .beginControlFlow(
            "return %L.getOrPut(%L(%T(%S), rootSegmentAlias))",
            builderCachePropertyName,
            GET_TARGET_FUN_NAME,
            SEGMENT,
            buildSegmentId(node)
          )
          .apply {
            if (node.parameter != null) {
              addStatement(
                "nodeFactory.%L(%L(%T(%S), payloads, rootSegmentAlias))",
                flowFactoryName,
                GET_PAYLOAD_FUN_NAME,
                SEGMENT,
                buildSegmentId(node)
              )
            } else {
              addStatement(
                "nodeFactory.%L()",
                flowFactoryName,
              )
            }
          }
          .endControlFlow()
          .build()
        lazyNodeBuilderFactories[node] = lazyPropertyBuilderFun
      }

      is Node.Screen -> {
        val screenBuilderFunSpec = FunSpec.builder("create${node.id.toPascalCase()}Node")
          .addModifiers(KModifier.ABSTRACT)
          .returns(SCREEN_NODE)
          .apply {
            if (node.parameter != null) {
              addParameter(node.parameter.name, ClassName.bestGuess(node.parameter.type))
            }
          }
          .build()
        factoryTypeSpecBuilder.addFunction(screenBuilderFunSpec)
        nodeBuilders[node] = screenBuilderFunSpec
      }
    }
  }
  return typeSpecBuilder
    .primaryConstructor(constructorBuilder.build())
    .apply {
      if (lazyNodeBuilderFactories.isNotEmpty()) {
        val builderCacheProperty = PropertySpec
          .builder(
            builderCachePropertyName,
            MUTABLE_MAP.parameterizedBy(PATH, NODE_BUILDER),
            KModifier.PRIVATE
          )
          .initializer("%T(%L)", ClassName("kotlin.collections", "HashMap"), lazyNodeBuilderFactories.size)
          .build()
        addProperty(builderCacheProperty)
        lazyNodeBuilderFactories.values.forEach { factory ->
          addFunction(factory)
        }
      }
    }
    .addType(factoryTypeSpecBuilder.build())
    .addSuperinterface(NODE_BUILDER)
    .addFunction(
      FunSpec.builder("build")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("path", PATH)
        .addParameter("payloads", MAP.parameterizedBy(PATH, ANY))
        .addParameter("rootSegmentAlias", SEGMENT.copy(nullable = true))
        .returns(NODE)
        .addCode(
          createBuildFunctionBody(
            flow,
            adjacencyList,
            lazyNodeBuilderFactories,
            nodeBuilders,
            isRootNode,
            schemaFilePath
          )
        )
        .build()
    )
    .addFunction(
      FunSpec.builder("invalidateCache")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("path", PATH)
        .apply {
          val includeDebug = true
          if (lazyNodeBuilderFactories.isNotEmpty()) {
            if (includeDebug) {
              beginControlFlow(
                "%L.keys.filter { !path.%M(it) }.forEach",
                builderCachePropertyName,
                MemberName(LIBRARY_PACKAGE, "startsWith"),
              )
              addStatement("println(%P)", "\${this::class.simpleName}: removing nodeBuilder for \$it")
              endControlFlow()
            }
            addStatement(
              "%L.keys.retainAll { path.%M(it) }",
              builderCachePropertyName,
              MemberName(LIBRARY_PACKAGE, "startsWith"),
            )
            beginControlFlow("%L.forEach { (builderPath, builder) ->", builderCachePropertyName)
            addStatement(
              "builder.invalidateCache(path.%M(builderPath.length - 1))",
              MemberName(LIBRARY_PACKAGE, "drop"),
            )
            endControlFlow()
          } else {
            addStatement("return Unit")
          }
        }
        .build()
    )
    .addFunction(buildGetTargetFunSpec())
    .addFunction(buildGetPayloadBySegmentIdFunSpec())
    .build()
}

private fun createBuildFunctionBody(
  flow: Node,
  adjacencyList: Map<Node, List<Node>>,
  lazyNodeBuilderFactories: Map<Node, FunSpec>,
  nodeBuilders: Map<Node, FunSpec>,
  isRootNode: Boolean,
  schemaFilePath: Path,
): CodeBlock {
  fun buildSegmentId(node: Node): String {
    return "${node.id}@$schemaFilePath"
  }

  return CodeBlock.builder()
    .addStatement(
      "val rootPath = rootSegmentAlias?.let { %T(it) } ?: %T(%T(%S))",
      PATH,
      PATH,
      SEGMENT,
      buildSegmentId(flow)
    )
    .beginControlFlow(
      "check(path.%M().id == rootPath.%M().id)",
      libraryMemberName("firstSegment"),
      libraryMemberName("firstSegment"),
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
                  "path == rootPath -> %L.%L(%L(rootPath.%M(), payloads, rootSegmentAlias))",
                  NODE_FACTORY_PARAMETER_NAME,
                  NODE_FACTORY_FLOW_NODE_BUILDER_NAME,
                  GET_PAYLOAD_FUN_NAME,
                  libraryMemberName("firstSegment")
                )
              } else {
                addStatement(
                  "path == rootPath -> %L.%L()",
                  NODE_FACTORY_PARAMETER_NAME,
                  NODE_FACTORY_FLOW_NODE_BUILDER_NAME
                )
              }
            } else {
              beginControlFlow(
                "path.%M(%L(%T(%S), rootSegmentAlias)) ->",
                MemberName(LIBRARY_PACKAGE, "startsWith"),
                GET_TARGET_FUN_NAME,
                SEGMENT,
                buildSegmentId(node),
              )
              addStatement(
                "val targetPath = %L(%T(%S), rootSegmentAlias)",
                GET_TARGET_FUN_NAME,
                SEGMENT,
                buildSegmentId(node),
              )
              if (node.parameter != null) {
                addStatement(
                  "val nodeBuilder = %N(payloads, rootSegmentAlias)",
                  lazyNodeBuilderFactories[node] ?: error("no lazy builder property for \"${node.id}\""),
                )
              } else {
                addStatement(
                  "val nodeBuilder = %N(rootSegmentAlias)",
                  lazyNodeBuilderFactories[node] ?: error("no lazy builder property for \"${node.id}\""),
                )
              }
              addStatement(
                "nodeBuilder.build(path.%M(targetPath.length·-·1)," +
                  " payloads·=·payloads.mapKeys·{·it.key.%M(targetPath.length·-·1)·}," +
                  " rootSegmentAlias·=·targetPath.%M())",
                MemberName(LIBRARY_PACKAGE, "drop"),
                MemberName(LIBRARY_PACKAGE, "drop"),
                MemberName(LIBRARY_PACKAGE, "lastSegment"),
              )
              endControlFlow()
            }
          }

          is Node.Screen -> {
            if (node.parameter != null) {
              addStatement(
                "path == %L(%T(%S), rootSegmentAlias) -> %L.%N(%L(%T(%S), payloads, rootSegmentAlias))",
                GET_TARGET_FUN_NAME,
                SEGMENT,
                buildSegmentId(node),
                NODE_FACTORY_PARAMETER_NAME,
                nodeBuilders[node] ?: error("no builder for screen node \"${node.id}\""),
                GET_PAYLOAD_FUN_NAME,
                SEGMENT,
                buildSegmentId(node)
              )
            } else {
              addStatement(
                "path == %L(%T(%S), rootSegmentAlias) -> %L.%N()",
                GET_TARGET_FUN_NAME,
                SEGMENT,
                buildSegmentId(node),
                NODE_FACTORY_PARAMETER_NAME,
                nodeBuilders[node] ?: error("no builder for screen node \"${node.id}\"")
              )
            }
          }
        }
      }
      addStatement("else -> error(%P)", "illegal path build requested for \"${flow.id}\" node: \$path")
    }
    .endControlFlow() // end "when"
    .build()
}

private fun buildGetTargetFunSpec(): FunSpec {
  return FunSpec.builder(GET_TARGET_FUN_NAME)
    .returns(PATH)
    .addParameter("segment", SEGMENT)
    .addParameter("rootSegmentAlias", SEGMENT.copy(nullable = true))
    .addCode(
      "return %L.target(%L.regions.first(),${NBSP}segment, rootSegmentAlias) ?: error(%P)",
      SCHEMA_PARAMETER_NAME,
      SCHEMA_PARAMETER_NAME,
      "internal error: no target generated for segment \"\${segment.id}\""
    )
    .build()
}

private fun buildGetPayloadBySegmentIdFunSpec(): FunSpec {
  return FunSpec.builder(GET_PAYLOAD_FUN_NAME)
    .addTypeVariable(TypeVariableName("T"))
    .returns(TypeVariableName("T"))
    .addAnnotation(
      AnnotationSpec.builder(Suppress::class)
        .addMember("%S", "UNCHECKED_CAST")
        .build()
    )
    .addParameter("segment", SEGMENT)
    .addParameter("payloads", MAP.parameterizedBy(PATH, ANY))
    .addParameter("rootSegmentAlias", SEGMENT.copy(nullable = true))
    .addCode(
      CodeBlock.builder()
        .addStatement("val targetPath = $GET_TARGET_FUN_NAME(segment, rootSegmentAlias)")
        .addStatement(
          "val payload = payloads[targetPath] ?: error(%P)",
          "no payload for \"\$targetPath\""
        )
        .addStatement("return payload as T")
        .build()
    )
    .build()
}

private const val NODE_FACTORY_FLOW_NODE_BUILDER_NAME = "createRootNode"
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
