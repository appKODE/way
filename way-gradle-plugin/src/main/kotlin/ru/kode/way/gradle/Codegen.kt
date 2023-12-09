package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import org.gradle.configurationcache.extensions.capitalized
import java.io.File
import java.nio.file.Path

internal fun generate(
  file: File,
  projectDir: File,
  outputDirectory: File,
  config: CodeGenConfig,
) {
  buildSpecs(file, projectDir, config).apply {
    schemaFileSpec.writeTo(outputDirectory)
    targetsFileSpec.writeTo(outputDirectory)
    nodeBuilderSpecs.forEach {
      it.writeTo(outputDirectory)
    }
  }
}

internal fun buildSpecs(
  file: File,
  projectDir: File,
  config: CodeGenConfig,
): SchemaOutputSpecs {
  val parseResult = parseSchemaDotFile(file, projectDir)
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

internal fun buildSegmentId(schemaFilePath: Path, node: Node): String {
  return "${node.id}@$schemaFilePath"
}

internal fun buildPathConstructorCall(
  nodes: List<Node>,
  buildSegmentId: (Node) -> String
): CodeBlock {
  return CodeBlock.builder()
    .add(
      "%T(listOf(%L))",
      libraryClassName("Path"),
      buildSegmentArgumentList(nodes, buildSegmentId)
    )
    .build()
}

internal fun buildSegmentArgumentList(
  nodes: List<Node>,
  buildSegmentId: (Node) -> String
): CodeBlock {
  return CodeBlock.builder()
    .add(
      buildString {
        for (index in (0..nodes.lastIndex)) {
          if (index > 0)
            append(", ")
          append("%T(%S)")
        }
      },
      *buildList {
        nodes.forEach { node ->
          add(libraryClassName("Segment"))
          add(buildSegmentId(node))
        }
      }.toTypedArray()
    )
    .build()
}

internal const val NBSP = '·'
