package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.util.PatternFilterable
import java.io.File
import java.nio.file.Paths

@CacheableTask
open class GenerateClassesTask : SourceTask() {
  // TODO Add this input, see
  //   https://github.com/cashapp/sqldelight/blob/master/sqldelight-compiler/build.gradle#L75
//  // Required to invalidate the task on version updates.
//  @Input
//  val pluginVersion = VERSION
  @Input
  var generatedSourcesDir: String = project.buildDir.absolutePath

  @get:OutputDirectory
  var outputDirectory: File = Paths.get("$generatedSourcesDir/generated/way/code").toFile()

  @InputFiles
  @SkipWhenEmpty
  @IgnoreEmptyDirectories
  @PathSensitive(PathSensitivity.RELATIVE)
  override fun getSource(): FileTree {
    return super.getSource()
  }

  @Input
  var packageName = LIBRARY_PACKAGE

  @Input
  var outputSchemaClassName = DEFAULT_SCHEMA_CLASS_NAME

  // TODO Use gradle workers api
  @TaskAction
  fun generate() {
    logger.warn("generation started")
    source.forEach { file ->
      parseSchemeDotFile(file)
      generate(
        file,
        CodeGenConfig(
          outputDir = outputDirectory,
          outputPackageName = packageName,
          outputSchemaClassName = outputSchemaClassName
        )
      )
    }
    FileSpec.builder(packageName, "AppFlowNodeBuilder")
      .addType(
        TypeSpec.classBuilder("AppFlowNodeBuilder")
          .addSuperinterface(libraryClassName("NodeBuilder"))
          .addFunction(
            FunSpec.builder("build")
              .addParameter("path", libraryClassName("Path"))
              .addModifiers(KModifier.OVERRIDE)
              .returns(libraryClassName("Node"))
              .addCode("TODO()")
              .build()
          )
          .build()
      )
      .build()
      .writeTo(outputDirectory)
  }
}

internal data class CodeGenConfig(
  val outputDir: File,
  val outputPackageName: String,
  val outputSchemaClassName: String,
)
