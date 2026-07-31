package ru.kode.way.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateClassesTask : SourceTask() {

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @get:Internal
  abstract val projectDirectory: DirectoryProperty

  init {
    outputDirectory.convention(project.layout.buildDirectory.dir("generated/way/code"))
    projectDirectory.convention(project.layout.projectDirectory)
  }

  @InputFiles
  @SkipWhenEmpty
  @IgnoreEmptyDirectories
  @PathSensitive(PathSensitivity.RELATIVE)
  override fun getSource(): FileTree = super.getSource()

  @Input
  var packageName = LIBRARY_PACKAGE

  @Input
  var outputSchemaClassName = DEFAULT_SCHEMA_CLASS_NAME

  // TODO Use gradle workers api
  @TaskAction
  fun generate() {
    val output = outputDirectory.get().asFile
    val projectDir = projectDirectory.get().asFile
    val config = CodeGenConfig(outputPackageName = packageName, outputSchemaClassName = outputSchemaClassName)
    output.deleteRecursively()
    output.mkdirs()
    logger.debug("generation started")
    val files = source.toList()
    // Parse all files first so we can detect output filename collisions before writing anything.
    val parseResults = files.map { file ->
      logger.debug("parsing schema file: $file")
      parseSchemaDotFile(file, projectDir, warn = logger::warn)
    }
    validateNoOutputFileCollisions(parseResults, config)
    // An empty (or whitespace-only) schema file parses to an empty adjacencyList with no
    // validator errors — SchemaRegistry.from() already skips these silently for registry-matching
    // purposes, but the codegen path below has no equivalent guard and would otherwise fail deep
    // inside codegen (e.g. a root-node lookup on an empty graph) with an unhelpful, unrelated
    // exception. Fail fast here with a message naming the specific file, since this most likely
    // means a flavor/buildType override file accidentally replaced a real base graph with a blank
    // stub.
    val emptySchemaFiles = parseResults.filter { it.adjacencyList.isEmpty() }
    if (emptySchemaFiles.isNotEmpty()) {
      error(
        emptySchemaFiles.joinToString("\n") { parseResult ->
          "${parseResult.filePath} parsed to an empty navigation graph — check for a blank or invalid override file."
        },
      )
    }
    // Build the cross-file registry once so every per-file codegen pass agrees on segment ids
    // at schema boundaries (parent emits the same id for `homeFlow [type=schema]` that the
    // child schema emits for its own rootSegment). See `SchemaRegistry`.
    val registry = SchemaRegistry.from(parseResults)
    parseResults.forEach { parseResult ->
      logger.debug("generating classes from schema: ${parseResult.graphId ?: "<default>"}")
      generateFromParseResult(parseResult, output, config, registry)
    }
  }
}

internal data class CodeGenConfig(val outputPackageName: String, val outputSchemaClassName: String)
