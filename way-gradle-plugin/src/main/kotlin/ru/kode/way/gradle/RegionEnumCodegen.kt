package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

/**
 * Emits an `enum class <SchemaBaseName>Region(val segmentName: String)` alongside the schema
 * for parallel-rooted top-level schemas (schemas with more than one direct region root).
 *
 * The generated enum gives consumers a stable, typed identifier per sub-region without forcing
 * them to compare [ru.kode.way.RegionId] values directly. Each entry carries the segment name
 * (the portion before `@file.dot`), and a companion `forRegionId` performs the reverse lookup.
 *
 * Returns null when the schema has zero or one region (flow-rooted schemas) — there is no enum
 * worth emitting in that case.
 */
internal fun buildRegionEnumFileSpecOrNull(parseResult: SchemaParseResult, config: CodeGenConfig): FileSpec? {
  val regionRoots = buildRegionRoots(parseResult.adjacencyList)
  if (regionRoots.size <= 1) return null

  val packageName = parseResult.customPackage ?: config.outputPackageName
  val schemaClassName = schemaClassName(parseResult, config)
  val enumSimpleName = regionEnumClassName(schemaClassName)
  val enumClassName = ClassName(packageName, enumSimpleName)

  val enumBuilder = TypeSpec.enumBuilder(enumClassName)
    .primaryConstructor(
      FunSpec.constructorBuilder()
        .addParameter(ParameterSpec.builder("segmentName", String::class).build())
        .build(),
    )
    .addProperty(
      PropertySpec.builder("segmentName", String::class)
        .initializer("segmentName")
        .build(),
    )

  regionRoots.forEach { regionRoot ->
    enumBuilder.addEnumConstant(
      regionRoot.id.toPascalCase(),
      TypeSpec.anonymousClassBuilder()
        .addSuperclassConstructorParameter("%S", regionRoot.id)
        .build(),
    )
  }

  // companion object { fun forRegionId(regionId: RegionId): <Enum>? = ... }
  enumBuilder.addType(
    TypeSpec.companionObjectBuilder()
      .addFunction(
        FunSpec.builder("forRegionId")
          .addParameter("regionId", REGION_ID)
          .returns(enumClassName.copy(nullable = true))
          .addStatement(
            // Emits `substringBefore('@')`; the delimiter is sourced from the same constant that
            // buildSegmentId uses to construct the id, keeping the two ends in lockstep.
            "val name = regionId.path.segments.last().id.substringBefore('$SEGMENT_ID_GRAPH_DELIMITER')",
          )
          .addStatement(
            "return entries.firstOrNull { it.segmentName == name }",
          )
          .build(),
      )
      .build(),
  )

  return FileSpec.builder(packageName, enumSimpleName)
    .addType(enumBuilder.build())
    .build()
}

internal fun regionEnumClassName(schemaClassName: String): String {
  val base = schemaClassName.removeSuffix("Schema")
  return "${base}Region"
}
