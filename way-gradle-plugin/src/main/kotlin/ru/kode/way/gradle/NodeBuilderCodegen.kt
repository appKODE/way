package ru.kode.way.gradle

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MUTABLE_MAP
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName

internal fun buildNodeBuilderFileSpecs(
  parseResult: SchemaParseResult,
  config: CodeGenConfig,
  registry: SchemaRegistry,
): List<FileSpec> {
  val packageName = parseResult.customPackage ?: config.outputPackageName
  val mainAdjList = parseResult.adjacencyList
  val mainRootNode = mainAdjList.findRootNode()
  val mainSchemaClassName = ClassName(packageName, schemaClassName(parseResult, config))

  // Every node that owns a virtual sub-schema — nested LocalParallels plus LOCAL flow children of
  // LocalParallel — needs its NodeBuilder generated against that sub-schema's view of the graph.
  val virtualRoots: Set<Node> = mainAdjList.virtualSubSchemaRoots().toSet()
  val virtualSubgraphs: Map<Node, AdjacencyList> = virtualRoots.associateWith { mainAdjList.subgraphFor(it) }

  return mainAdjList.mapFlow { flow, _ ->
    val className = ClassName(packageName, nodeBuilderClassName(flow))
    val owner = resolveNodeBuilderOwner(
      flow,
      mainAdjList,
      mainSchemaClassName,
      mainRootNode,
      virtualRoots,
      virtualSubgraphs,
      packageName,
    )
    FileSpec
      .builder(
        packageName,
        className.simpleName,
      )
      .addType(
        buildNodeBuilderTypeSpec(
          flow = flow,
          className = className,
          schemaClassName = owner.schemaClassName,
          adjacencyList = owner.adjacencyList,
          isRootNode = flow == owner.rootNode,
          parseResult = parseResult,
          registry = registry,
        ),
      )
      .build()
  }
}

/** The schema view a flow's NodeBuilder is generated against — its own virtual sub-schema, or the main schema. */
private data class NodeBuilderOwner(
  val adjacencyList: AdjacencyList,
  val schemaClassName: ClassName,
  val rootNode: Node,
)

/**
 * Resolves the [NodeBuilderOwner] for [flow]: the innermost ancestor (inclusive) that owns a virtual
 * sub-schema — whose graph/schema/root drive the flow's path lookups (`schema.target`,
 * `schema.nodeType`) — or the main outer schema when [flow] belongs to no virtual sub-schema.
 */
private fun resolveNodeBuilderOwner(
  flow: Node,
  mainAdjList: AdjacencyList,
  mainSchemaClassName: ClassName,
  mainRootNode: Node,
  virtualRoots: Set<Node>,
  virtualSubgraphs: Map<Node, AdjacencyList>,
  packageName: String,
): NodeBuilderOwner {
  val owner = findOwnerVirtualSubSchemaRoot(flow, mainAdjList, virtualRoots)
    ?: return NodeBuilderOwner(mainAdjList, mainSchemaClassName, mainRootNode)
  return NodeBuilderOwner(
    adjacencyList = virtualSubgraphs[owner]!!,
    schemaClassName = ClassName(packageName, virtualSchemaClassName(owner)),
    rootNode = owner,
  )
}

/**
 * Returns the innermost ancestor (inclusive) of [flow] that owns a virtual sub-schema, or `null`
 * when [flow] belongs to the main outer schema.
 */
private fun findOwnerVirtualSubSchemaRoot(flow: Node, mainAdjList: AdjacencyList, virtualRoots: Set<Node>): Node? {
  var current: Node = flow
  while (true) {
    if (current in virtualRoots) return current
    current = mainAdjList.findParent(current) ?: return null
  }
}

internal fun buildNodeBuilderTypeSpec(
  flow: Node.Flow,
  className: ClassName,
  schemaClassName: ClassName,
  adjacencyList: AdjacencyList,
  isRootNode: Boolean,
  parseResult: SchemaParseResult,
  registry: SchemaRegistry,
): TypeSpec {
  fun buildSegmentId(node: Node): String = buildSegmentId(node, parseResult, registry)

  val typeSpecBuilder = TypeSpec.classBuilder(className)
  val constructorBuilder = FunSpec.constructorBuilder()
  val nodeBuilders = mutableMapOf<Node, FunSpec>()
  val lazyNodeBuilderFactories = mutableMapOf<Node, FunSpec>()

  val factoryBuilderTypeName = className.nestedClass("Factory")
  val factoryTypeSpecBuilder = TypeSpec.interfaceBuilder(factoryBuilderTypeName)
    .addFunction(
      FunSpec.builder(ROOT_NODE_FACTORY_METHOD_NAME)
        .addModifiers(KModifier.ABSTRACT)
        .returns(
          when (flow) {
            is Node.Flow.Local -> FLOW_NODE.parameterizedBy(STAR)
            is Node.Flow.LocalParallel -> PARALLEL_FLOW_NODE.parameterizedBy(STAR)
            is Node.Flow.Imported -> error("unexpected node type: ${flow::class.simpleName}")
          },
        )
        .apply {
          flow.parameter?.let { param -> addParameter(param.name, parseTypeName(param.type)) }
        }
        .build(),
    )

  val factoryParameter = ParameterSpec
    .builder(
      NODE_FACTORY_PARAMETER_NAME,
      factoryBuilderTypeName,
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
      schemaClassName,
    )
    .build()
  val schemaProperty = PropertySpec.builder(schemaParameter.name, schemaParameter.type, KModifier.OVERRIDE)
    .initializer(schemaParameter.name)
    .build()
  constructorBuilder.addParameter(schemaParameter)
  typeSpecBuilder.addProperty(schemaProperty)

  dfsWhile(adjacencyList, flow) { node ->
    if (node == flow) return@dfsWhile true // skip root, but DO descend
    val shouldDescend = shouldDescendInto(node, flow)
    // See NOTE_GROUPING_NODES_BY_FLOW_RULE — foreign nodes are handled by their own flow's NodeBuilder.
    if (isForeignToFlowScope(node, flow, isRootNode, adjacencyList)) return@dfsWhile shouldDescend
    when (node) {
      is Node.Flow -> {
        val flowFactoryName = "create${node.id.toPascalCase()}NodeBuilder"
        factoryTypeSpecBuilder.addFunction(
          FunSpec.builder(flowFactoryName)
            .addModifiers(KModifier.ABSTRACT)
            .returns(NODE_BUILDER)
            .apply {
              node.parameter?.let { param -> addParameter(param.name, parseTypeName(param.type)) }
            }
            .build(),
        )
        val lazyPropertyBuilderFun = if (adjacencyList.isFanIn(node)) {
          buildFanInLazyNodeBuilderFunSpec(node, flowFactoryName)
        } else {
          FunSpec
            .builder("${node.id}NodeBuilder")
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
              NODE_BUILDER_CACHE_PROPERTY_NAME,
              TARGET_OR_ERROR_FUN_NAME,
              SEGMENT,
              buildSegmentId(node),
            )
            .apply {
              if (node.parameter != null) {
                addStatement(
                  "nodeFactory.%L(%L(%T(%S), payloads, rootSegmentAlias))",
                  flowFactoryName,
                  PAYLOAD_OR_ERROR_FUN_NAME,
                  SEGMENT,
                  buildSegmentId(node),
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
        }
        lazyNodeBuilderFactories[node] = lazyPropertyBuilderFun
      }

      is Node.Screen -> {
        val screenBuilderFunSpec = FunSpec.builder("create${node.id.toPascalCase()}Node")
          .addModifiers(KModifier.ABSTRACT)
          .returns(SCREEN_NODE)
          .apply {
            if (node.parameter != null) {
              addParameter(node.parameter.name, parseTypeName(node.parameter.type))
            }
          }
          .build()
        factoryTypeSpecBuilder.addFunction(screenBuilderFunSpec)
        nodeBuilders[node] = screenBuilderFunSpec
      }

      // History nodes are never built: no factory method, no lazy builder.
      is Node.History -> Unit
    }
    shouldDescend
  }
  return typeSpecBuilder
    .primaryConstructor(constructorBuilder.build())
    .apply {
      if (lazyNodeBuilderFactories.isNotEmpty()) {
        val builderCacheProperty = PropertySpec
          .builder(
            NODE_BUILDER_CACHE_PROPERTY_NAME,
            MUTABLE_MAP.parameterizedBy(PATH, NODE_BUILDER),
            KModifier.PRIVATE,
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
    .apply {
      // For parallel nodes: emit named val <childId>RegionId constants so users never need
      // to hardcode Path strings for sub-region lookups.
      if (flow is Node.Flow.LocalParallel) {
        adjacencyList[flow].orEmpty().forEach { child ->
          addProperty(
            PropertySpec
              .builder("${child.id}RegionId", REGION_ID)
              .getter(
                FunSpec.getterBuilder()
                  .addCode(
                    "return %T(%L)",
                    REGION_ID,
                    buildPathConstructorCall(reversedParents(child, adjacencyList)) { node -> buildSegmentId(node) },
                  )
                  .build(),
              )
              .build(),
          )
        }
      }
    }
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
            parseResult,
            registry,
          ),
        )
        .build(),
    )
    .addFunction(
      FunSpec.builder("invalidateCache")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("alivePaths", SET.parameterizedBy(PATH))
        .apply {
          if (lazyNodeBuilderFactories.isNotEmpty()) {
            addStatement(
              "%L.keys.retainAll·{·key·->·alivePaths.any·{·it.%M(key)·}·}",
              NODE_BUILDER_CACHE_PROPERTY_NAME,
              MemberName(LIBRARY_PACKAGE, "startsWith"),
            )
            beginControlFlow("%L.forEach·{·(builderPath,·builder)·->", NODE_BUILDER_CACHE_PROPERTY_NAME)
            addStatement("val·drop·=·builderPath.length·-·1")
            addStatement(
              "val·childAlive·=·alivePaths.filter·{·it.%M(builderPath)·&&·it.length·>·drop·}" +
                ".map·{·it.%M(drop)·}.toSet()",
              MemberName(LIBRARY_PACKAGE, "startsWith"),
              MemberName(LIBRARY_PACKAGE, "drop"),
            )
            addStatement("builder.invalidateCache(childAlive)")
            endControlFlow()
          } else {
            addStatement("return Unit")
          }
        }
        .build(),
    )
    .apply {
      if (lazyNodeBuilderFactories.isNotEmpty()) {
        addFunction(
          FunSpec.builder("snapshotCache")
            .addModifiers(KModifier.OVERRIDE)
            .returns(ANY.copy(nullable = true))
            .addStatement(
              "return %L.mapValues·{·(_,·builder)·->·builder·to·builder.snapshotCache()·}",
              NODE_BUILDER_CACHE_PROPERTY_NAME,
            )
            .build(),
        )
        addFunction(
          FunSpec.builder("restoreCache")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("snapshot", ANY.copy(nullable = true))
            .addStatement("%L.clear()", NODE_BUILDER_CACHE_PROPERTY_NAME)
            .beginControlFlow("(snapshot·as·%T<*,·*>).forEach·{·(path,·saved)·->", MAP)
            .addStatement("val·(builder,·childSnapshot)·=·saved·as·%T<*,·*>", ClassName("kotlin", "Pair"))
            .addStatement("%L[path·as·%T]·=·builder·as·%T", NODE_BUILDER_CACHE_PROPERTY_NAME, PATH, NODE_BUILDER)
            .addStatement("builder.restoreCache(childSnapshot)")
            .endControlFlow()
            .build(),
        )
      }
    }
    .addFunction(buildTargetOrErrorFunSpec())
    .apply {
      if (lazyNodeBuilderFactories.keys.any { adjacencyList.isFanIn(it) }) addFunction(buildTargetsOrErrorFunSpec())
    }
    .addFunction(buildPayloadOrErrorFunSpec())
    .apply {
      // Only emit the path-keyed payload helpers which are called: by the root branch of build() when the root flow
      // has a parameter, and by the lazy builder of a parameterized imported schema with several parents.
      // Skipping them otherwise keeps the generated NodeBuilder minimal.
      val rootPayloadNullable = flow.parameter?.let { parseTypeName(it.type).isNullable }
      rootPayloadNullable?.let { addFunction(buildRootPayloadFunSpec(it)) }
      val fanInPayload = lazyNodeBuilderFactories.keys.any { adjacencyList.isFanIn(it) && it.parameter != null }
      if (fanInPayload && rootPayloadNullable != false) addFunction(buildRootPayloadFunSpec(isNullable = false))
    }
    .build()
}

/**
 * Whether a dfsWhile scoped to [flow]'s subtree should descend into [node]'s children. A nested
 * LocalParallel owns its own NodeBuilder, so it is processed but NOT descended into; everything else
 * is descended.
 */
private fun shouldDescendInto(node: Node, flow: Node): Boolean = !(node is Node.Flow.LocalParallel && node != flow)

/**
 * True when [node] is FOREIGN to a dfsWhile scoped to [flow] and must be skipped for processing (but
 * still descended): a screen whose nearest parent flow isn't [flow], or a non-root flow other than
 * [flow] itself. Such nodes are emitted by their own flow's NodeBuilder.
 */
private fun isForeignToFlowScope(node: Node, flow: Node, isRootNode: Boolean, adjacencyList: AdjacencyList): Boolean =
  (node is Node.Screen && adjacencyList.findParentFlow(node) != flow) ||
    (node is Node.Flow && !isRootNode && node != flow)

private fun createBuildFunctionBody(
  flow: Node.Flow,
  adjacencyList: AdjacencyList,
  lazyNodeBuilderFactories: Map<Node, FunSpec>,
  nodeBuilders: Map<Node, FunSpec>,
  isRootNode: Boolean,
  parseResult: SchemaParseResult,
  registry: SchemaRegistry,
): CodeBlock {
  fun buildSegmentId(node: Node): String = buildSegmentId(node, parseResult, registry)

  return CodeBlock.builder()
    .addStatement(
      "val rootPath = rootSegmentAlias?.let { %T(it) } ?: %T(%T(%S))",
      PATH,
      PATH,
      SEGMENT,
      buildSegmentId(flow),
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
      val nodes = mutableListOf<Node>()
      dfsWhile(adjacencyList, flow) { node ->
        // See NOTE_GROUPING_NODES_BY_FLOW_RULE — foreign nodes route through their own flow's NodeBuilder.
        if (!isForeignToFlowScope(node, flow, isRootNode, adjacencyList)) nodes.add(node)
        shouldDescendInto(node, flow)
      }
      // A flow declared inside of another flow of this schema goes first: the outer one is a prefix of its paths too.
      // Only the flows are reordered, each of the other nodes stays where the traversal has put it.
      val isNestedFlow = { node: Node -> node is Node.Flow && node != flow }
      val nestedFlows = nodes.filter(isNestedFlow)
        .sortedByDescending { node -> adjacencyList.parentChains(node).maxOf { it.size } }
        .iterator()
      nodes.map { node -> if (isNestedFlow(node)) nestedFlows.next() else node }.forEach { node ->
        when (node) {
          is Node.Flow -> {
            if (node == flow) {
              val param = node.parameter
              if (param != null) {
                addStatement(
                  "path == rootPath -> %L.%L(%L(rootPath, payloads))",
                  NODE_FACTORY_PARAMETER_NAME,
                  ROOT_NODE_FACTORY_METHOD_NAME,
                  rootPayloadFunName(parseTypeName(param.type).isNullable),
                )
              } else {
                addStatement(
                  "path == rootPath -> %L.%L()",
                  NODE_FACTORY_PARAMETER_NAME,
                  ROOT_NODE_FACTORY_METHOD_NAME,
                )
              }
            } else if (adjacencyList.isFanIn(node)) {
              // An imported schema reachable from several parents: build it on whichever of its paths is requested.
              beginControlFlow(
                "%L(%T(%S), rootSegmentAlias).any·{·path.%M(it)·} ->",
                TARGETS_OR_ERROR_FUN_NAME,
                SEGMENT,
                buildSegmentId(node),
                MemberName(LIBRARY_PACKAGE, "startsWith"),
              )
              addStatement(
                "val targetPath = %L(%T(%S), rootSegmentAlias).first·{·path.%M(it)·}",
                TARGETS_OR_ERROR_FUN_NAME,
                SEGMENT,
                buildSegmentId(node),
                MemberName(LIBRARY_PACKAGE, "startsWith"),
              )
              addStatement(
                if (node.parameter !=
                  null
                ) {
                  "val nodeBuilder = %N(targetPath, payloads)"
                } else {
                  "val nodeBuilder = %N(targetPath)"
                },
                lazyNodeBuilderFactories[node] ?: error("no lazy builder property for \"${node.id}\""),
              )
              addNestedBuildStatement()
              endControlFlow()
            } else {
              beginControlFlow(
                "path.%M(%L(%T(%S), rootSegmentAlias)) ->",
                MemberName(LIBRARY_PACKAGE, "startsWith"),
                TARGET_OR_ERROR_FUN_NAME,
                SEGMENT,
                buildSegmentId(node),
              )
              addStatement(
                "val targetPath = %L(%T(%S), rootSegmentAlias)",
                TARGET_OR_ERROR_FUN_NAME,
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
              addNestedBuildStatement()
              endControlFlow()
            }
          }

          is Node.Screen -> {
            if (node.parameter != null) {
              addStatement(
                "path == %L(%T(%S), rootSegmentAlias) -> %L.%N(%L(%T(%S), payloads, rootSegmentAlias))",
                TARGET_OR_ERROR_FUN_NAME,
                SEGMENT,
                buildSegmentId(node),
                NODE_FACTORY_PARAMETER_NAME,
                nodeBuilders[node] ?: error("no builder for screen node \"${node.id}\""),
                PAYLOAD_OR_ERROR_FUN_NAME,
                SEGMENT,
                buildSegmentId(node),
              )
            } else {
              addStatement(
                "path == %L(%T(%S), rootSegmentAlias) -> %L.%N()",
                TARGET_OR_ERROR_FUN_NAME,
                SEGMENT,
                buildSegmentId(node),
                NODE_FACTORY_PARAMETER_NAME,
                nodeBuilders[node] ?: error("no builder for screen node \"${node.id}\""),
              )
            }
          }

          // History nodes are never built, so they emit no routing branch.
          is Node.History -> Unit
        }
      }
      addStatement("else -> error(%P)", "illegal path build requested for \"${flow.id}\" node: \$path")
    }
    .endControlFlow() // end "when"
    .build()
}

// The `.filterKeys { it.length > targetPath.length - 1 }` before mapKeys is a drop
// safety guard: `Path.drop(n)` on a Path with `n` segments would yield an empty
// segment list and fail Path's `isNotEmpty` init check. Filter-first removes the
// payloads keys that can't survive the drop — by definition those keys refer to
// ancestors above the current cascade level, which the inner build no longer needs.
private fun CodeBlock.Builder.addNestedBuildStatement() = addStatement(
  "nodeBuilder.build(path.%M(targetPath.length·-·1)," +
    " payloads·=·payloads.filterKeys·{·it.length·>·targetPath.length·-·1·}" +
    ".mapKeys·{·it.key.%M(targetPath.length·-·1)·}," +
    " rootSegmentAlias·=·targetPath.%M())",
  MemberName(LIBRARY_PACKAGE, "drop"),
  MemberName(LIBRARY_PACKAGE, "drop"),
  MemberName(LIBRARY_PACKAGE, "lastSegment"),
)

/**
 * The lazy builder of an imported schema reachable from several parents: cached per path, the payload is looked up
 * at that path. Takes the resolved `targetPath` instead of `rootSegmentAlias`, as the segment alone is ambiguous.
 */
private fun buildFanInLazyNodeBuilderFunSpec(node: Node.Flow, flowFactoryName: String): FunSpec = FunSpec
  .builder("${node.id}NodeBuilder")
  .addModifiers(KModifier.PRIVATE)
  .returns(NODE_BUILDER)
  .addParameter("targetPath", PATH)
  .apply { if (node.parameter != null) addParameter("payloads", MAP.parameterizedBy(PATH, ANY)) }
  .beginControlFlow("return %L.getOrPut(targetPath)", NODE_BUILDER_CACHE_PROPERTY_NAME)
  .apply {
    if (node.parameter != null) {
      addStatement("nodeFactory.%L(%L(targetPath, payloads))", flowFactoryName, ROOT_PAYLOAD_OR_ERROR_FUN_NAME)
    } else {
      addStatement("nodeFactory.%L()", flowFactoryName)
    }
  }
  .endControlFlow()
  .build()

private fun buildTargetsOrErrorFunSpec(): FunSpec = FunSpec.builder(TARGETS_OR_ERROR_FUN_NAME)
  .returns(LIST.parameterizedBy(PATH))
  .addParameter("segment", SEGMENT)
  .addParameter("rootSegmentAlias", SEGMENT.copy(nullable = true))
  .addCode(
    "return %L.regions.firstNotNullOfOrNull·{·%L.targets(it,${NBSP}segment, rootSegmentAlias).ifEmpty·{·null·}·}" +
      "·?: error(%P)",
    SCHEMA_PARAMETER_NAME,
    SCHEMA_PARAMETER_NAME,
    "internal error: no target generated for segment \"\${segment.id}\"",
  )
  .build()

private fun buildTargetOrErrorFunSpec(): FunSpec = FunSpec.builder(TARGET_OR_ERROR_FUN_NAME)
  .returns(PATH)
  .addParameter("segment", SEGMENT)
  .addParameter("rootSegmentAlias", SEGMENT.copy(nullable = true))
  .addCode(
    "return %L.regions.firstNotNullOfOrNull·{·%L.target(it,${NBSP}segment, rootSegmentAlias)·}·?: error(%P)",
    SCHEMA_PARAMETER_NAME,
    SCHEMA_PARAMETER_NAME,
    "internal error: no target generated for segment \"\${segment.id}\"",
  )
  .build()

private fun buildPayloadOrErrorFunSpec(): FunSpec = FunSpec.builder(PAYLOAD_OR_ERROR_FUN_NAME)
  .addTypeVariable(TypeVariableName("T"))
  .returns(TypeVariableName("T"))
  .addAnnotation(
    AnnotationSpec.builder(Suppress::class)
      .addMember("%S", "UNCHECKED_CAST")
      .build(),
  )
  .addParameter("segment", SEGMENT)
  .addParameter("payloads", MAP.parameterizedBy(PATH, ANY))
  .addParameter("rootSegmentAlias", SEGMENT.copy(nullable = true))
  .addCode(
    CodeBlock.builder()
      .addStatement("val targetPath = $TARGET_OR_ERROR_FUN_NAME(segment, rootSegmentAlias)")
      .addStatement("val payload = payloads[targetPath] ?: throw %T(targetPath)", MISSING_PAYLOAD_EXCEPTION)
      .addStatement("return (if (payload === %T) null else payload) as T", NULL_PAYLOAD)
      .build(),
  )
  .build()

// Direct path-keyed payload lookup. Used for the root branch of NodeBuilder.build, where
// rootPath is already known and looking up via `targetOrError(rootSegment)` would walk
// `schema.regions` searching for the root — which fails for parallel-flow roots (whose own
// segment is the parent of all regions, not a member of any). NavigationService.start()
// places the root payload at rootPath directly (NavigationService.kt:166-175), so we
// retrieve it from there. A nullable root parameter gets `payloadAtPathOrNull` instead: start(null)
// stores no payload at all, which for a nullable parameter means a null argument, not an error.
private fun buildRootPayloadFunSpec(isNullable: Boolean): FunSpec = FunSpec.builder(rootPayloadFunName(isNullable))
  .addTypeVariable(TypeVariableName("T"))
  .returns(TypeVariableName("T"))
  .addAnnotation(
    AnnotationSpec.builder(Suppress::class)
      .addMember("%S", "UNCHECKED_CAST")
      .build(),
  )
  .addParameter("path", PATH)
  .addParameter("payloads", MAP.parameterizedBy(PATH, ANY))
  .addCode(
    CodeBlock.builder()
      .apply {
        if (isNullable) {
          addStatement("val payload = payloads[path]")
        } else {
          addStatement("val payload = payloads[path] ?: throw %T(path)", MISSING_PAYLOAD_EXCEPTION)
        }
      }
      .addStatement("return (if (payload === %T) null else payload) as T", NULL_PAYLOAD)
      .build(),
  )
  .build()

// These constants hold the NAMES of functions/properties emitted into the generated NodeBuilder;
// the string values are part of the generated code and must not change.
private const val ROOT_NODE_FACTORY_METHOD_NAME = "createRootNode"
private const val NODE_FACTORY_PARAMETER_NAME = "nodeFactory"
private const val SCHEMA_PARAMETER_NAME = "schema"
private const val NODE_BUILDER_CACHE_PROPERTY_NAME = "nodeBuilders"
private const val TARGET_OR_ERROR_FUN_NAME = "targetOrError"
private const val TARGETS_OR_ERROR_FUN_NAME = "targetsOrError"
private const val PAYLOAD_OR_ERROR_FUN_NAME = "payloadOrError"
private const val ROOT_PAYLOAD_OR_ERROR_FUN_NAME = "payloadAtPathOrError"
private const val ROOT_PAYLOAD_OR_NULL_FUN_NAME = "payloadAtPathOrNull"

private fun rootPayloadFunName(isNullable: Boolean): String =
  if (isNullable) ROOT_PAYLOAD_OR_NULL_FUN_NAME else ROOT_PAYLOAD_OR_ERROR_FUN_NAME

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
