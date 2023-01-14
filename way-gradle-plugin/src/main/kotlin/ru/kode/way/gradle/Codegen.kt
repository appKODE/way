package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import org.gradle.api.Task
import java.io.File

internal fun Task.generate(file: File, config: CodeGenConfig) {
  logger.warn("generating classes from schema file: $file")
  FileSpec.builder(config.outputPackageName, config.outputSchemaClassName)
    .addType(
      TypeSpec.classBuilder(ClassName(config.outputPackageName, config.outputSchemaClassName))
        .addSuperinterface(libraryClassName("Schema"))
        .build()
    )
    .build()
    .writeTo(config.outputDir)
}

internal fun libraryClassName(name: String): ClassName {
  return ClassName(LIBRARY_PACKAGE, name)
}
