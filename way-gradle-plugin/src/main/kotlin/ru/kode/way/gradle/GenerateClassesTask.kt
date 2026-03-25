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
    logger.debug("generation started")
    source.forEach { file ->
      logger.debug("generating classes from schema file: $file")
      generate(
        file,
        projectDir,
        output,
        CodeGenConfig(
          outputPackageName = packageName,
          outputSchemaClassName = outputSchemaClassName,
        ),
      )
    }
  }
}

internal data class CodeGenConfig(val outputPackageName: String, val outputSchemaClassName: String)
