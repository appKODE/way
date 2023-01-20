package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.TypeSpec
import java.io.File

internal fun generate(file: File, outputDirectory: File, config: CodeGenConfig) {
  buildSpecs(file, config).apply {
    schemaFileSpec.writeTo(outputDirectory)
  }
}

internal fun buildSpecs(
  file: File,
  config: CodeGenConfig
): SchemaOutputSpecs {
  val parseResult = parseSchemaDotFile(file = file, packageName = config.outputPackageName)
  return SchemaOutputSpecs(
    schemaFileSpec = buildSchemaFileSpec(parseResult, config),
    nodeBuilderSpecs = emptyList()
  )
}

internal fun buildSchemaFileSpec(parseResult: SchemaParseResult, config: CodeGenConfig): FileSpec {
  val schemaClassName = parseResult.graphId?.let { "${it}Schema" } ?: config.outputSchemaClassName
  val schemaFileSpec = FileSpec.builder(
    config.outputPackageName,
    parseResult.customSchemaFileName ?: schemaClassName
  )
  val schemaTypeSpec = TypeSpec
    .classBuilder(name = schemaClassName)
    .addSuperinterface(libraryClassName("Schema"))
    .addProperty(
      PropertySpec.builder("regions", LIST.parameterizedBy(libraryClassName("RegionId")), KModifier.OVERRIDE)
        .initializer("listOf(%T(%T(%S)))", libraryClassName("RegionId"), libraryClassName("Path"), "app")
        .build()
    )
    .addFunction(
      FunSpec.builder("children")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("regionId", libraryClassName("RegionId"))
        .returns(SET.parameterizedBy(libraryClassName("Segment")))
        .addCode("return emptySet()")
        .build()
    )
    .addFunction(
      FunSpec.builder("children")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("regionId", libraryClassName("RegionId"))
        .addParameter("segment", libraryClassName("Segment"))
        .returns(SET.parameterizedBy(libraryClassName("Segment")))
        .addCode("return emptySet()")
        .build()
    )
    .addFunction(
      FunSpec.builder("targets")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("regionId", libraryClassName("RegionId"))
        .returns(MAP.parameterizedBy(libraryClassName("Segment"), libraryClassName("Path")))
        .addCode("return emptyMap()")
        .build()
    )
    .addFunction(
      FunSpec.builder("nodeType")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("regionId", libraryClassName("RegionId"))
        .addParameter("path", libraryClassName("Path"))
        .returns(libraryClassName("Schema").nestedClass("NodeType"))
        .addCode("return Schema.NodeType.Flow")
        .build()
    )
  return schemaFileSpec.addType(schemaTypeSpec.build()).build()
}


internal class SchemaOutputSpecs(
  val schemaFileSpec: FileSpec,
  val nodeBuilderSpecs: List<FileSpec>,
)

internal fun libraryClassName(name: String): ClassName {
  return ClassName(LIBRARY_PACKAGE, name)
}
