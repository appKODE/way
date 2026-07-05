package ru.kode.way.gradle

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import java.io.File
import java.util.Locale

internal fun generate(file: File, projectDir: File, outputDirectory: File, config: CodeGenConfig) {
  val parseResult = parseSchemaDotFile(file, projectDir)
  generateFromParseResult(parseResult, outputDirectory, config, SchemaRegistry.from(listOf(parseResult)))
}

internal fun generateFromParseResult(
  parseResult: SchemaParseResult,
  outputDirectory: File,
  config: CodeGenConfig,
  registry: SchemaRegistry = SchemaRegistry.from(listOf(parseResult)),
) {
  buildSpecs(parseResult, config, registry).apply {
    schemaFileSpec.writeTo(outputDirectory)
    targetsFileSpec.writeTo(outputDirectory)
    nodeBuilderSpecs.forEach { it.writeTo(outputDirectory) }
    finishEventsFileSpecs.forEach { it.writeTo(outputDirectory) }
    virtualSchemaFileSpecs.forEach { it.writeTo(outputDirectory) }
    regionEnumFileSpec?.writeTo(outputDirectory)
  }
}

/**
 * Cross-file resolver: maps a node id (the name a parent uses on a `type=schema` imported node)
 * to the [SchemaParseResult] of the `.dot` file that actually declares that node as its root.
 *
 * Built once per gradle codegen pass from the full list of `.dot` files in the source set, then
 * consulted by [buildSegmentId] whenever it needs to emit a [Segment] id for an
 * [Node.Flow.Imported] node. The point is to make parent and child codegen agree on the
 * **same** segment id for the same logical node — without this, the parent stamps its own
 * `@file` on the boundary segment while the child stamps the child's, and `Path.endsWith`
 * comparisons between absolute paths (built by the runtime using parent-side ids) and
 * schema-local region ids (using child-side ids) fail at every schema mount boundary.
 *
 * When an Imported node has no matching `.dot` file in the registry — e.g. tests that pass a
 * single fixture file with bare Imported references — [resolveSource] returns `null` and the
 * caller falls back to the owner's identity. This preserves the existing single-file codegen
 * behavior and is fine for any setup where the child schema is hand-rolled instead of
 * codegenned.
 */
internal class SchemaRegistry private constructor(private val byRootNodeName: Map<String, List<SchemaParseResult>>) {
  /**
   * Returns the unique [SchemaParseResult] whose root node is named [importedNodeName], or `null`
   * when no schema or multiple schemas match. When the lookup is ambiguous (test source sets
   * routinely have multiple unrelated fixtures with the same root, but only one is genuinely the
   * import target), the caller falls back to the owner's identity — same shape as the
   * pre-unification behavior, scoped by `@file`. Production codebases where each `.dot` lives in
   * its own feature module have a unique match per root name and benefit from unification.
   *
   * Pass [importingPackage] to prefer same-package matches as a disambiguator when multiple
   * schemas share a root name across separate sub-trees. If exactly one same-package candidate
   * exists, it wins; otherwise the lookup remains ambiguous and returns `null`.
   */
  fun resolveSource(importedNodeName: String, importingPackage: String? = null): SchemaParseResult? {
    val matches = byRootNodeName[importedNodeName] ?: return null
    if (matches.size == 1) return matches.single()
    if (importingPackage != null) {
      val samePackage = matches.filter { it.customPackage == importingPackage }
      if (samePackage.size == 1) return samePackage.single()
    }
    return null
  }

  companion object {
    fun from(parseResults: List<SchemaParseResult>): SchemaRegistry {
      val byRootNodeName = mutableMapOf<String, MutableList<SchemaParseResult>>()
      parseResults.forEach { pr ->
        if (pr.adjacencyList.isEmpty()) return@forEach
        val rootNode = pr.adjacencyList.findRootNode()
        byRootNodeName.getOrPut(rootNode.id) { mutableListOf() }.add(pr)
      }
      return SchemaRegistry(byRootNodeName)
    }
  }
}

// Enumerates every (package, fileName) pair this code-generator will emit and reports collisions
// across all generators (schema, targets, NodeBuilder, finish-event interfaces, virtual schemas).
// NOTE: If FinishEventsCodegen.childFinishRequestInterfaceName or NodeBuilderCodegen's class-naming
// logic ever changes, this validator must follow — otherwise collision detection will silently miss
// overlaps.
internal fun validateNoOutputFileCollisions(parseResults: List<SchemaParseResult>, config: CodeGenConfig) {
  data class OutputKey(val pkg: String, val fileName: String)
  // Collect each parseResult's outputs into a Set so a single file that triggers the same output
  // name through multiple code paths (e.g. MainChildFinishRequest once per parallel-region child)
  // doesn't look like a self-collision. Cross-parseResult duplicates are the real signal.
  val outputsByOrigin: Map<String, Set<OutputKey>> = parseResults.associate { pr ->
    val pkg = pr.customPackage ?: config.outputPackageName
    val outs = mutableSetOf<OutputKey>()
    fun add(fileName: String) {
      outs.add(OutputKey(pkg, fileName))
    }
    add(schemaFileName(pr, config))
    add(targetsFileName(pr))
    // NodeBuilders: must match NodeBuilderCodegen which calls mapFlow — excludes Node.Flow.Imported
    // (imported flows are built from their own .dot file's parseResult).
    pr.adjacencyList.forEach { (node, _) ->
      if (node is Node.Flow.Local || node is Node.Flow.LocalParallel) {
        add(nodeBuilderClassName(node))
      }
    }
    pr.adjacencyList.virtualSubSchemaRoots().forEach { root ->
      add(virtualSchemaClassName(root))
    }
    if (pr.adjacencyList.isNotEmpty()) {
      val regionRoots = buildRegionRoots(pr.adjacencyList)
      regionRoots.forEach { rr ->
        val parallelParent = pr.adjacencyList.parallelParentOf(rr)
        if (parallelParent != null && rr is Node.Flow) {
          add(childFinishRequestInterfaceName(parallelParent.id))
        }
        add(childFinishRequestInterfaceName(rr.id))
      }
    }
    pr.filePath.toString() to outs
  }
  val collisionMap = mutableMapOf<OutputKey, MutableList<String>>()
  for ((origin, outs) in outputsByOrigin) {
    outs.forEach { key -> collisionMap.getOrPut(key) { mutableListOf() }.add(origin) }
  }
  val dups = collisionMap.filter { it.value.size > 1 }
  if (dups.isNotEmpty()) {
    error(
      "Multiple DOT schema files produce overlapping output file(s):\n" +
        dups.entries.joinToString("\n") { (k, v) ->
          "  ${k.pkg}.${k.fileName}.kt ← ${v.joinToString()}"
        } +
        "\nResolve by setting unique graphId attributes or renaming nodes.",
    )
  }
}

internal fun buildSpecs(file: File, projectDir: File, config: CodeGenConfig): SchemaOutputSpecs {
  val parseResult = parseSchemaDotFile(file, projectDir)
  return buildSpecs(parseResult, config, SchemaRegistry.from(listOf(parseResult)))
}

internal fun buildSpecs(
  parseResult: SchemaParseResult,
  config: CodeGenConfig,
  registry: SchemaRegistry,
): SchemaOutputSpecs {
  // Roots that own a virtual sub-schema: nested LocalParallels AND LOCAL flows that are direct
  // children of a LocalParallel. Both kinds need their own schema so that paths inside their
  // NodeBuilders are anchored at the sub-region root (matching the documented `rootSegmentAlias`
  // contract). See [virtualSubSchemaRoots] for the rationale.
  val virtualRoots = parseResult.adjacencyList.virtualSubSchemaRoots()
  val virtualSchemaSpecs = virtualRoots.map { root ->
    val subAdjList = parseResult.adjacencyList.subgraphFor(root)
    val virtualParseResult = parseResult.copy(
      adjacencyList = subAdjList,
      // Keep the parent's graphId — virtual sub-schemas represent a slice of the same .dot file,
      // and their segments must carry the same `@graphId:file` suffix as segments emitted by the
      // outer schema so absolute paths compare cleanly across boundaries.
      customSchemaFileName = virtualSchemaClassName(root),
      customSchemaClassName = virtualSchemaClassName(root),
      customTargetsFileName = null,
    )
    buildSchemaFileSpec(
      virtualParseResult,
      config.copy(outputSchemaClassName = virtualSchemaClassName(root)),
      registry,
    )
  }
  // For each virtual sub-schema root, emit finish-event interfaces for any flow descendants. The
  // virtual schema's createChildFlowFinishRequestEvent body references e.g. AlphaChildFinishRequest.AlphaSub
  // (keyed by region-root id inside the virtual graph); without these specs that interface is never emitted.
  // Pass emitParentParallelEntries=false to avoid duplicating the outer schema's parent-parallel entries.
  val virtualFinishEventSpecs = virtualRoots.flatMap { root ->
    val subAdjList = parseResult.adjacencyList.subgraphFor(root)
    val virtualParseResult = parseResult.copy(
      adjacencyList = subAdjList,
      customSchemaFileName = null,
      customTargetsFileName = null,
    )
    buildChildFinishEventFileSpecs(
      virtualParseResult,
      config.copy(outputSchemaClassName = virtualSchemaClassName(root)),
      registry,
      emitParentParallelEntries = false,
    )
  }
  // For top-level schemas with more than one region (i.e. parallel-rooted), emit a Region enum
  // so consumers can use a typed identifier for each sub-region instead of comparing RegionId
  // values directly. Single-region (flow-rooted) schemas have no meaningful enum to emit.
  val regionEnumSpec = buildRegionEnumFileSpecOrNull(parseResult, config)
  return SchemaOutputSpecs(
    schemaFileSpec = buildSchemaFileSpec(parseResult, config, registry),
    targetsFileSpec = buildTargetsFileSpec(parseResult, config, registry),
    nodeBuilderSpecs = buildNodeBuilderFileSpecs(parseResult, config, registry),
    finishEventsFileSpecs = buildChildFinishEventFileSpecs(parseResult, config, registry) + virtualFinishEventSpecs,
    virtualSchemaFileSpecs = virtualSchemaSpecs,
    regionEnumFileSpec = regionEnumSpec,
  )
}

internal class SchemaOutputSpecs(
  val schemaFileSpec: FileSpec,
  val targetsFileSpec: FileSpec,
  val nodeBuilderSpecs: List<FileSpec>,
  val finishEventsFileSpecs: List<FileSpec>,
  val virtualSchemaFileSpecs: List<FileSpec> = emptyList(),
  val regionEnumFileSpec: FileSpec? = null,
)

/**
 * Returns the class name for the virtual Schema generated for a nested [Node.Flow.LocalParallel].
 * This mirrors the naming used for imported-schema classes (e.g. "Par06AlphaSchema").
 */
internal fun virtualSchemaClassName(node: Node): String = node.id.toPascalCase() + "Schema"

internal fun libraryMemberName(name: String): MemberName = MemberName(LIBRARY_PACKAGE, name)

internal fun String.toPascalCase(): String = replaceFirstChar {
  if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
}

/**
 * Builds the `Segment.id` string used in generated code. Format:
 * `<nodeName>@<sourceGraphId>:<sourceFilePath>`.
 *
 * `sourceGraphId` is the `digraph X` name from whichever `.dot` file actually defines the node:
 * - For [Node.Flow.Imported] (i.e. a `type=schema` reference) — the **child** schema's graph id,
 *   resolved via [SchemaRegistry]. This is the key insight: the parent that imports the child
 *   and the child itself must emit the **same** segment id for that boundary node, otherwise
 *   `Path.endsWith` / `Path.startsWith` fail at every schema mount.
 * - For everything else (Local, LocalParallel, Screen) — the `owner` schema's graph id (the
 *   `.dot` file currently being codegenned).
 *
 * If [registry] has no match for an Imported node (single-file test fixtures, hand-rolled child
 * schemas), this falls back to the `owner`'s identity — same as the pre-unification behavior.
 */
internal fun buildSegmentId(node: Node, owner: SchemaParseResult, registry: SchemaRegistry): String {
  val source: SchemaParseResult = if (node is Node.Flow.Imported) {
    registry.resolveSource(node.id, importingPackage = owner.customPackage) ?: owner
  } else {
    owner
  }
  return "${node.id}$SEGMENT_ID_GRAPH_DELIMITER${source.graphId ?: "_"}:${source.filePath}"
}

/**
 * Delimiter between the node name and the `<graphId>:<file>` disambiguator inside a `Segment.id`
 * (see [buildSegmentId]). [Segment.name]-style consumers strip everything from this character on to
 * recover the bare node name (e.g. RegionEnumCodegen); keep the two in lockstep.
 */
internal const val SEGMENT_ID_GRAPH_DELIMITER = '@'

/** The generated Schema file/class name for [pr] — the custom name when set, else [schemaClassName]. */
internal fun schemaFileName(pr: SchemaParseResult, config: CodeGenConfig): String =
  pr.customSchemaFileName ?: schemaClassName(pr, config)

/** The generated Targets file/class name for [pr] — `<graphId>Targets`, or the custom/default name. */
internal fun targetsFileName(pr: SchemaParseResult): String =
  pr.customTargetsFileName ?: (pr.graphId?.let { "${it}Targets" } ?: DEFAULT_TARGETS_FILE_NAME)

/** The generated NodeBuilder class name for a flow [node] — `<PascalId>NodeBuilder`. */
internal fun nodeBuilderClassName(node: Node): String = "${node.id.toPascalCase()}NodeBuilder"

internal fun buildPathConstructorCall(nodes: List<Node>, buildSegmentId: (Node) -> String): CodeBlock =
  CodeBlock.builder()
    .add(
      "%T(listOf(%L))",
      PATH,
      buildSegmentArgumentList(nodes, buildSegmentId),
    )
    .build()

/**
 * Emits `Segment("a"), Segment("b"), …` — one `Segment(...)` literal per node in [nodes].
 *
 * INVARIANT: the format string and the argument list must stay in lockstep at 2 args per node — each
 * `%T(%S)` placeholder consumes exactly one `SEGMENT` type and one segment-id string. Changing one
 * side without the other corrupts the generated output.
 */
internal fun buildSegmentArgumentList(nodes: List<Node>, buildSegmentId: (Node) -> String): CodeBlock =
  CodeBlock.builder()
    .add(
      buildString {
        for (index in (0..nodes.lastIndex)) {
          if (index > 0) {
            append(", ")
          }
          append("%T(%S)")
        }
      },
      *buildList {
        nodes.forEach { node ->
          add(SEGMENT)
          add(buildSegmentId(node))
        }
      }.toTypedArray(),
    )
    .build()

internal const val NBSP = '·'
