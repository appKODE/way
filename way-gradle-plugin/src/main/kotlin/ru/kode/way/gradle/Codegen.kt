package ru.kode.way.gradle

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import java.io.File
import java.nio.file.Path
import java.util.Locale

internal fun generate(file: File, projectDir: File, outputDirectory: File, config: CodeGenConfig) {
  buildSpecs(file, projectDir, config).apply {
    schemaFileSpec.writeTo(outputDirectory)
    targetsFileSpec.writeTo(outputDirectory)
    nodeBuilderSpecs.forEach {
      it.writeTo(outputDirectory)
    }
    finishEventsFileSpec?.writeTo(outputDirectory)
  }
}

internal fun buildSpecs(file: File, projectDir: File, config: CodeGenConfig): SchemaOutputSpecs {
  val parseResult = parseSchemaDotFile(file, projectDir)
  return SchemaOutputSpecs(
    schemaFileSpec = buildSchemaFileSpec(parseResult, config),
    targetsFileSpec = buildTargetsFileSpec(parseResult, config),
    nodeBuilderSpecs = buildNodeBuilderFileSpecs(parseResult, config),
    finishEventsFileSpec = buildChildFinishEventFileSpecs(parseResult, config),
  )
}

internal class SchemaOutputSpecs(
  val schemaFileSpec: FileSpec,
  val targetsFileSpec: FileSpec,
  val nodeBuilderSpecs: List<FileSpec>,
  val finishEventsFileSpec: FileSpec?,
)

internal fun libraryMemberName(name: String): MemberName = MemberName(LIBRARY_PACKAGE, name)

internal fun String.toPascalCase(): String = replaceFirstChar {
  if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
}

internal fun String.toCamelCase(): String = this

internal fun buildSegmentId(schemaFilePath: Path, node: Node): String = "${node.id}@$schemaFilePath"

internal fun buildPathConstructorCall(nodes: List<Node>, buildSegmentId: (Node) -> String): CodeBlock =
  CodeBlock.builder()
    .add(
      "%T(listOf(%L))",
      PATH,
      buildSegmentArgumentList(nodes, buildSegmentId),
    )
    .build()

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
