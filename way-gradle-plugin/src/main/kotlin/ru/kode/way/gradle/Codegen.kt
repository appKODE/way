package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import org.gradle.api.Task
import java.io.File

internal fun Task.generate(file: File, outputDirectory: File, config: CodeGenConfig) {
  logger.warn("generating classes from schema file: $file")
  parseSchemeDotFile(file = file, packageName = config.outputPackageName)
    .writeTo(outputDirectory)
}

internal fun libraryClassName(name: String): ClassName {
  return ClassName(LIBRARY_PACKAGE, name)
}
