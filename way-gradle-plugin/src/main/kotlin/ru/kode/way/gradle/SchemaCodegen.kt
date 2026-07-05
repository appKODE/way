package ru.kode.way.gradle

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT

internal fun buildSchemaFileSpec(
  parseResult: SchemaParseResult,
  config: CodeGenConfig,
  registry: SchemaRegistry,
): FileSpec {
  val schemaClassName = schemaClassName(parseResult, config)
  val packageName = parseResult.customPackage ?: config.outputPackageName
  val schemaFileSpec = FileSpec.builder(
    packageName,
    schemaFileName(parseResult, config),
  )
  val regionRoots = buildRegionRoots(parseResult.adjacencyList)
  val constructorParameters = buildConstructorParameters(parseResult.adjacencyList)
  val constructorProperties = constructorParameters.map {
    PropertySpec.builder(it.name, it.type, KModifier.PRIVATE)
      .initializer(it.name)
      .build()
  }

  fun buildSegmentId(node: Node): String = buildSegmentId(node, parseResult, registry)

  val schemaTypeSpec = TypeSpec.classBuilder(name = schemaClassName)
    .apply {
      if (constructorParameters.isNotEmpty()) {
        primaryConstructor(FunSpec.constructorBuilder().addParameters(constructorParameters).build())
        addProperties(constructorProperties)
      }
    }
    .addSuperinterface(SCHEMA)
    .addProperty(
      buildRootSegmentProperty(parseResult.adjacencyList, ::buildSegmentId),
    )
    .addProperty(
      buildChildSchemasProperty(parseResult.adjacencyList, ::buildSegmentId),
    )
    .addProperty(
      buildRegionsPropertySpec(regionRoots, parseResult.adjacencyList, ::buildSegmentId),
    )
    .apply {
      // Per-region typed accessors: `public val <regionRoot>RegionId: RegionId get() = regions[i]`
      // Lets callers reference a region by its name without indexing into the list.
      regionRoots.forEachIndexed { index, regionRoot ->
        addProperty(
          PropertySpec.builder("${regionRoot.id}RegionId", REGION_ID)
            .getter(FunSpec.getterBuilder().addStatement("return regions[$index]").build())
            .build(),
        )
      }
    }
    .addFunction(
      buildSchemaTargetsSpec(parseResult.adjacencyList, ::buildSegmentId),
    )
    .addFunction(
      buildSchemaNodeTypeSpec(parseResult.adjacencyList, ::buildSegmentId),
    )
    .addFunction(
      buildCreateChildFlowFinishEventSpec(packageName, parseResult.adjacencyList, ::buildSegmentId),
    )
  return schemaFileSpec.addType(schemaTypeSpec.build()).build()
}

private fun buildRegionsPropertySpec(
  regionRoots: List<Node>,
  adjacencyList: AdjacencyList,
  buildSegmentId: (Node) -> String,
): PropertySpec = PropertySpec.Companion.builder(
  "regions",
  LIST.parameterizedBy(REGION_ID),
  KModifier.OVERRIDE,
)
  .initializer(
    CodeBlock.builder()
      .add("listOf(")
      .apply {
        regionRoots.forEachIndexed { index, r ->
          add(
            "%T(%L)",
            REGION_ID,
            buildPathConstructorCall(reversedParents(r, adjacencyList), buildSegmentId),
          )
          if (index != regionRoots.lastIndex) {
            add(", ")
          }
        }
      }
      .add(")")
      .build(),
  )
  .build()

private fun buildRootSegmentProperty(adjacencyList: AdjacencyList, buildSegmentId: (Node) -> String): PropertySpec {
  val rootNode = adjacencyList.findRootNode()
  return PropertySpec
    .builder("rootSegment", SEGMENT, KModifier.OVERRIDE)
    .initializer(
      CodeBlock.builder()
        .add(
          "%T(%S)",
          SEGMENT,
          buildSegmentId(rootNode),
        )
        .build(),
    )
    .build()
}

private fun buildChildSchemasProperty(adjacencyList: AdjacencyList, buildSegmentId: (Node) -> String): PropertySpec {
  val rootNode = adjacencyList.findRootNode()
  val importedFlowNodes = mutableListOf<Node>()
  dfs(adjacencyList, rootNode) { node ->
    when (node) {
      is Node.Flow.Imported -> importedFlowNodes.add(node)

      is Node.Flow.Local,
      is Node.Flow.LocalParallel,
      is Node.Screen,
      is Node.History,
      -> Unit
    }
  }
  // Virtual sub-schema roots — nested LocalParallels and LOCAL flow children of LocalParallel —
  // also contribute child-schema entries. Runtime helpers like `findParentSchema` and
  // `maybeResolveInitial` walk `childSchemas` to descend; without these entries a nested
  // parallel's `regions` lookup falls back to its outer parent's regions and either reports the
  // wrong sub-regions or recurses forever.
  //
  // Each virtual sub-schema is instantiated inline. It inherits the same Imported-schema
  // constructor properties this outer schema holds — virtual sub-graphs may contain Imported
  // flows, and we thread those through by forwarding the relevant constructor properties.
  val virtualRoots = adjacencyList.virtualSubSchemaRoots()
  val entries = importedFlowNodes.map { it to ChildSchemaSource.Imported } +
    virtualRoots.map { it to ChildSchemaSource.Virtual }
  val childSchemasProperty = PropertySpec.builder(
    "childSchemas",
    MAP.parameterizedBy(SEGMENT, SCHEMA),
    KModifier.OVERRIDE,
  )
  if (entries.isEmpty()) {
    return childSchemasProperty.initializer("emptyMap()").build()
  }
  return childSchemasProperty
    .initializer(
      CodeBlock.builder()
        .add("mapOf(")
        .apply {
          entries.forEachIndexed { index, (node, source) ->
            when (source) {
              ChildSchemaSource.Imported -> add(
                "%T(%S)·to·%L",
                SEGMENT,
                buildSegmentId(node),
                schemaConstructorPropertyName(node),
              )

              ChildSchemaSource.Virtual -> {
                // Compute the Imported flows the virtual sub-schema's constructor expects (the
                // sub-graph's own Importeds), and pass through this outer schema's matching
                // properties so the runtime sees a consistent schema tree.
                //
                // These args are forwarded POSITIONALLY. That is safe because the virtual schema's
                // own constructor params come from `buildConstructorParameters(subAdjList)` — i.e.
                // `subAdjList` map order — and `subAdjList` is the LinkedHashMap that `subgraphFor`
                // fills in DFS order, so this fresh `dfs(subAdjList, root)` visits Imported flows in
                // the identical order. Pinned by AdjacencyListTest's "positional forwarding is safe".
                val subAdjList = adjacencyList.subgraphFor(node)
                val virtualImportedFlows = mutableListOf<Node>()
                dfs(subAdjList, subAdjList.findRootNode()) { n ->
                  if (n is Node.Flow.Imported) virtualImportedFlows.add(n)
                }
                add(
                  "%T(%S)·to·%L(%L)",
                  SEGMENT,
                  buildSegmentId(node),
                  virtualSchemaClassName(node),
                  virtualImportedFlows.joinToString(", ") { schemaConstructorPropertyName(it) },
                )
              }
            }
            if (index != entries.lastIndex) add(", ")
          }
        }
        .add(")")
        .build(),
    )
    .build()
}

private enum class ChildSchemaSource { Imported, Virtual }

// `buildRegionRoots` moved to AdjacencyList.kt (with `internal` visibility) so other codegen
// files can reuse it. Keeping a duplicate `private` definition here caused overload-resolution
// ambiguity at the call sites in this file.

private fun buildConstructorParameters(adjacencyList: AdjacencyList): List<ParameterSpec> {
  val parameters = mutableListOf<ParameterSpec>()
  adjacencyList.forEach { (node, _) ->
    if (node is Node.Flow.Imported) {
      parameters.add(
        ParameterSpec.builder(schemaConstructorPropertyName(node), SCHEMA)
          .build(),
      )
    }
  }
  return parameters
}

private fun buildSchemaTargetsSpec(adjacencyList: AdjacencyList, buildSegmentId: (Node) -> String): FunSpec {
  // `rootSegment` is anchored at the schema's own root, NOT the regionRoot.
  // For parallel-flow-rooted schemas (top-level or virtual sub-schemas for nested parallels),
  // the schema root is the parallel parent and regionRoots are its sub-region children, so paths
  // become `Path(rootSegment, regionRoot, ...)`. For regular flow-rooted schemas the schema root
  // IS the (single) regionRoot, so paths reduce to `Path(rootSegment, ...)` with no extra hop.
  // This contract matches the documented behavior of `rootSegmentAlias` (NodeBuilder.kt:22-38):
  // the alias replaces the schema's *rootSegment*, not the regionRoot.
  val schemaRoot = adjacencyList.findRootNode()
  return FunSpec.builder("target")
    .addModifiers(KModifier.OVERRIDE)
    .addParameter("regionId", REGION_ID)
    .addParameter("segment", SEGMENT)
    .addParameter("rootSegmentAlias", SEGMENT.copy(nullable = true))
    .returns(PATH.copy(nullable = true))
    .addCode(
      CodeBlock.builder()
        .beginControlFlow("return·when·(regionId)·{")
        .apply {
          buildRegionRoots(adjacencyList).forEachIndexed { regionRootIndex, regionRoot ->
            beginControlFlow("regions[$regionRootIndex] -> {")
            addStatement(
              "val rootSegment = rootSegmentAlias ?: %T(%S)",
              SEGMENT,
              buildSegmentId(schemaRoot),
            )
            beginControlFlow("when(segment.id) {")
            // Emit one case per node in the schema's full subgraph. Paths are anchored at the
            // schema's `rootSegment` (see `emitTargetCase`) so every region branch produces the
            // same absolute paths — the per-region branching only exists to satisfy `regions[i] ->`
            // pattern matching, the path layout itself is region-independent.
            //
            // We deliberately cover the entire subgraph (not just descendants of `regionRoot`) so
            // that intermediate nodes between the schema root and the region roots — e.g. a
            // nested LocalParallel sitting between an outer `parallelFlow` schema root and its
            // tab sub-regions — also have a `target` entry. Without this, `AcmeMainFlowSchema.target`
            // would not resolve a query for the nested `acmeTabsFlow` segment and the runtime would
            // crash in `targetOrError` with "no target generated for segment".
            //
            // TODO @AdjacencyMatrix
            //  not very efficient: running DFS and then for each node inspecting all adjacency list to find parent
            //  adjacency matrix would allow to find parent nodes more easily.
            //  This stuff is going on in many places during codegen, search for them if will be optimizing
            // History nodes are codegen-time-only and are never built/resolved at runtime; their
            // HistoryTarget carries the PARENT FLOW path, so no target case is emitted for them.
            forEachUniqueRuntimeNode(adjacencyList, buildSegmentId) { node ->
              emitTargetCase(node, adjacencyList, buildSegmentId)
            }
            addStatement("else -> null")
            endControlFlow() // when (segment.name)
            endControlFlow() // regions[index] -> {
          }
        }
        .beginControlFlow("else -> {")
        .addStatement("error(%P)", "unknown regionId=\$regionId")
        .endControlFlow()
        .endControlFlow() // return when
        .build(),
    )
    .build()
}

/**
 * Runs [emit] for each unique BUILDABLE node reachable from the schema root — skipping History
 * pseudostates and de-duplicating by segment id — in DFS order. Shared by the `target()` and
 * `nodeType()` case emitters so both cover the identical node set in the identical order.
 */
private fun CodeBlock.Builder.forEachUniqueRuntimeNode(
  adjacencyList: AdjacencyList,
  buildSegmentId: (Node) -> String,
  emit: CodeBlock.Builder.(Node) -> Unit,
) {
  val emitted = mutableSetOf<String>()
  dfs(adjacencyList, adjacencyList.findRootNode()) { node ->
    if (node !is Node.History && emitted.add(buildSegmentId(node))) {
      emit(node)
    }
  }
}

/**
 * Emits a `<segmentId> -> Path(...)` line for [node] inside a `target()` `when(segment.id)` block.
 * The emitted Path is anchored at the schema's root (the local `rootSegment` variable). When [node]
 * IS the schema root, the path collapses to `Path(rootSegment)`; otherwise it is
 * `Path(listOf(rootSegment, <intermediates>, node))` where the intermediates are the chain from the
 * schema root down to [node] (excluding the schema root itself).
 */
private fun CodeBlock.Builder.emitTargetCase(
  node: Node,
  adjacencyList: AdjacencyList,
  buildSegmentId: (Node) -> String,
) {
  val intermediates = descendantChainFromSchemaRoot(node, adjacencyList)
  if (intermediates.isEmpty()) {
    addStatement(
      "%S -> %T(rootSegment)",
      buildSegmentId(node),
      PATH,
    )
  } else {
    addStatement(
      "%S -> %T(listOf(rootSegment, %L))",
      buildSegmentId(node),
      PATH,
      buildSegmentArgumentList(intermediates, buildSegmentId),
    )
  }
}

private fun buildSchemaNodeTypeSpec(adjacencyList: AdjacencyList, buildSegmentId: (Node) -> String): FunSpec {
  val schemaRoot = adjacencyList.findRootNode()
  return FunSpec.builder("nodeType")
    .addModifiers(KModifier.OVERRIDE)
    .addParameter("regionId", REGION_ID)
    .addParameter("path", PATH)
    .addParameter("rootSegmentAlias", SEGMENT.copy(nullable = true))
    .returns(SCHEMA.nestedClass("NodeType"))
    .addCode(
      CodeBlock.builder()
        .beginControlFlow("return when (regionId) {")
        .apply {
          buildRegionRoots(adjacencyList).forEachIndexed { regionRootIndex, regionRoot ->
            beginControlFlow("regions[$regionRootIndex] -> {")
            addStatement(
              "val rootSegment = rootSegmentAlias ?: %T(%S)",
              SEGMENT,
              buildSegmentId(schemaRoot),
            )
            beginControlFlow("when {")
            // Emit one case per node in the schema's full subgraph, mirroring `target()`. Covers
            // intermediate parallel nodes between the schema root and region roots so
            // `checkSchemaValidity` does not hit `else -> error` for paths like `<parallelRoot>`
            // or `<schemaRoot>.<nestedParallel>`.
            // History nodes never reach the runtime nodeType machinery (their target carries the
            // parent flow path), so no nodeType case is emitted for them.
            forEachUniqueRuntimeNode(adjacencyList, buildSegmentId) { node ->
              emitNodeTypeCase(node, adjacencyList, buildSegmentId)
            }
            beginControlFlow("else -> {")
            addStatement("error(%P)", "internal error: no nodeType for path=\$path")
            endControlFlow() // else -> {
            endControlFlow() // when {
            endControlFlow() // regions[i] -> {
          }
          beginControlFlow("else -> {")
          addStatement("error(%P)", "unknown regionId=\$regionId")
          endControlFlow()
        }
        .endControlFlow() // return when (regionId) {
        .build(),
    )
    .build()
}

/**
 * Emits a `path == Path(...) -> Schema.NodeType.X` line inside a `nodeType()` `when {}` block.
 * The path is anchored at the schema's root (the local `rootSegment` variable) using the same
 * layout rules as [emitTargetCase].
 */
private fun CodeBlock.Builder.emitNodeTypeCase(
  node: Node,
  adjacencyList: AdjacencyList,
  buildSegmentId: (Node) -> String,
) {
  val nodeTypeName = when (node) {
    is Node.Flow.Local, is Node.Flow.Imported -> "Flow"

    is Node.Flow.LocalParallel -> "ParallelFlow"

    is Node.Screen -> "Screen"

    // History nodes are filtered out before this point (they have no runtime nodeType).
    is Node.History -> error("internal error: history node \"${node.id}\" has no runtime nodeType")
  }
  val intermediates = descendantChainFromSchemaRoot(node, adjacencyList)
  if (intermediates.isEmpty()) {
    addStatement(
      "path == %T(rootSegment) -> %T.NodeType.$nodeTypeName",
      PATH,
      SCHEMA,
    )
  } else {
    addStatement(
      "path == %T(listOf(rootSegment, %L)) -> %T.NodeType.$nodeTypeName",
      PATH,
      buildSegmentArgumentList(intermediates, buildSegmentId),
      SCHEMA,
    )
  }
}

private fun buildCreateChildFlowFinishEventSpec(
  packageName: String,
  adjacencyList: AdjacencyList,
  buildSegmentId: (Node) -> String,
): FunSpec = FunSpec.builder("createChildFlowFinishRequestEvent")
  .addModifiers(KModifier.OVERRIDE)
  .addParameter("regionId", REGION_ID)
  .addParameter("path", PATH)
  .addParameter("result", ANY)
  .returns(EVENT)
  .addCode(
    CodeBlock.builder()
      .beginControlFlow("return when (regionId) {")
      .apply {
        buildRegionRoots(adjacencyList).forEachIndexed { regionRootIndex, regionRoot ->
          beginControlFlow("regions[$regionRootIndex] -> {")
          beginControlFlow("when(path) {")
          // When the regionRoot is itself a Flow child of a LocalParallel, the runtime invokes
          // this function via `computeSubRegionFinishBuilder` with a SINGLE-segment path —
          // `Path(Segment(regionRoot.id))` — to bubble the regionRoot's own finish to its
          // LocalParallel parent. Emit that single-segment case explicitly; without it the
          // runtime hits the `else -> error(...)` branch and parallel-sub-region finishes crash.
          val parallelParent = adjacencyList.parallelParentOf(regionRoot)
          if (parallelParent != null && regionRoot is Node.Flow) {
            val regionRootResultType = regionRoot.resultType
            if (regionRootResultType != UNIT.canonicalName) {
              val resultType = parseTypeName(regionRootResultType)
              addStatement(
                "%T(%T(%S)) -> %T(result as %T)",
                PATH,
                SEGMENT,
                buildSegmentId(regionRoot),
                childFinishRequestEventClassName(
                  packageName = packageName,
                  flowNodeId = parallelParent.id,
                  childFlowNodeId = regionRoot.id,
                ),
                resultType,
              )
            } else {
              addStatement(
                "%T(%T(%S)) -> %T",
                PATH,
                SEGMENT,
                buildSegmentId(regionRoot),
                childFinishRequestEventClassName(
                  packageName = packageName,
                  flowNodeId = parallelParent.id,
                  childFlowNodeId = regionRoot.id,
                ),
              )
            }
          }
          // For regionRoots that own a virtual sub-schema (LOCAL flow children of LocalParallel)
          // the descendant DFS would reference interfaces that THIS schema doesn't emit — those
          // interfaces are owned by the virtual sub-schema, and at runtime `findParentSchema`
          // descends into the virtual sub-schema before reaching `createChildFlowFinishRequestEvent`
          // for those descendants. Emitting the descendant cases here would create dead code that
          // also refers to undeclared classes (compile error).
          val regionRootOwnsVirtualSubSchema = adjacencyList.isLocalChildOfParallel(regionRoot)
          if (regionRootOwnsVirtualSubSchema) {
            beginControlFlow("else -> {")
            addStatement("error(%P)", "internal error: failed to build child finish event for path=\$path")
            endControlFlow() // else -> {
            endControlFlow() // when {
            endControlFlow() // regions[i] -> {
            return@forEachIndexed
          }
          dfs(adjacencyList, regionRoot) { node ->
            when (node) {
              is Node.Flow -> {
                if (node.id != regionRoot.id) {
                  if (node.resultType != UNIT.canonicalName) {
                    val resultType = parseTypeName(node.resultType)
                    addStatement(
                      "%T(listOf(rootSegment, %L)) -> %T(result as %T)",
                      PATH,
                      buildSegmentArgumentList(descendantChainFromSchemaRoot(node, adjacencyList), buildSegmentId),
                      childFinishRequestEventClassName(
                        packageName = packageName,
                        flowNodeId = regionRoot.id,
                        childFlowNodeId = node.id,
                      ),
                      resultType,
                    )
                  } else {
                    addStatement(
                      "%T(listOf(rootSegment, %L)) -> %T",
                      PATH,
                      buildSegmentArgumentList(descendantChainFromSchemaRoot(node, adjacencyList), buildSegmentId),
                      childFinishRequestEventClassName(
                        packageName = packageName,
                        flowNodeId = regionRoot.id,
                        childFlowNodeId = node.id,
                      ),
                    )
                  }
                }
              }

              is Node.Screen,
              is Node.History,
              -> Unit
            }
          }
          beginControlFlow("else -> {")
          addStatement("error(%P)", "internal error: failed to build child finish event for path=\$path")
          endControlFlow() // else -> {
          endControlFlow() // when {
          endControlFlow() // regions[i] -> {
        }
        beginControlFlow("else -> {")
        addStatement("error(%P)", "unknown regionId=\$regionId")
        endControlFlow()
      }
      .endControlFlow() // return when (regionId) {
      .build(),
  )
  .build()

private fun schemaConstructorPropertyName(node: Node) = "${node.id}Schema"

internal fun schemaClassName(parseResult: SchemaParseResult, config: CodeGenConfig): String =
  // `customSchemaClassName` is set for virtual sub-schemas — they share the parent file's
  // graphId (so their segment ids carry the same `@graphId:filePath` suffix and match across
  // boundaries) but need a distinct Kotlin class name to avoid colliding with the outer schema.
  // For regular schemas, fall back to `<graphId>Schema` and finally to the config default.
  // Note: `customSchemaFileName` controls only the .kt file name (test fixtures sometimes set it
  // to a path-style string that is NOT a valid Kotlin identifier), so it must NOT influence the
  // class name here.
  parseResult.customSchemaClassName
    ?: parseResult.graphId?.let { "${it}Schema" }
    ?: config.outputSchemaClassName

internal fun reversedParents(node: Node, adjacencyList: AdjacencyList): List<Node> = adjacencyList
  .findAllParents(node, includeThis = true)
  .reversed()

/**
 * The chain of nodes from the schema root down to [node] EXCLUDING the schema root itself, in
 * root-to-node order (`reversedParents(node).drop(1)`). These are the intermediate segments used to
 * anchor a node's absolute path at the generated `rootSegment` in `target()` / `nodeType()` /
 * finish-event cases.
 */
private fun descendantChainFromSchemaRoot(node: Node, adjacencyList: AdjacencyList): List<Node> =
  reversedParents(node, adjacencyList).drop(1)
