package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import org.gradle.configurationcache.extensions.capitalized
import java.io.File

internal fun generate(file: File, outputDirectory: File, config: CodeGenConfig) {
  buildSpecs(file, config).apply {
    schemaFileSpec.writeTo(outputDirectory)
    targetsFileSpec.writeTo(outputDirectory)
    nodeBuilderSpecs.forEach {
      it.writeTo(outputDirectory)
    }
  }
}

internal fun buildSpecs(
  file: File,
  config: CodeGenConfig
): SchemaOutputSpecs {
  val parseResult = parseSchemaDotFile(file = file)
  return SchemaOutputSpecs(
    schemaFileSpec = buildSchemaFileSpec(parseResult, config),
    targetsFileSpec = buildTargetsFileSpec(parseResult, config),
    nodeBuilderSpecs = buildNodeBuilderFileSpecs(parseResult, config)
  )
}

internal class SchemaOutputSpecs(
  val schemaFileSpec: FileSpec,
  val targetsFileSpec: FileSpec,
  val nodeBuilderSpecs: List<FileSpec>,
)

internal fun libraryClassName(name: String): ClassName {
  return ClassName(LIBRARY_PACKAGE, name)
}

internal fun libraryMemberName(name: String): MemberName {
  return MemberName(LIBRARY_PACKAGE, name)
}

internal fun String.toPascalCase(): String {
  return this.capitalized()
}

internal fun String.toCamelCase(): String {
  return this
}
