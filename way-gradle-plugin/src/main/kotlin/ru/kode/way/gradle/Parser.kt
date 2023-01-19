package ru.kode.way.gradle

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.TypeSpec
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import ru.kode.way.gradle.DotParser.GraphContext
import java.io.File

internal fun parseSchemaDotFile(
  file: File,
  packageName: String,
): FileSpec {
  val stream = CommonTokenStream(DotLexer(CharStreams.fromPath(file.toPath())))
  val parser = DotParser(stream)
  val parseTree = parser.graph()
  val visitor = Visitor(
    packageName = packageName,
    defaultOutputFileName = file.name,
  )
  visitor.visitGraph(parseTree)
  return visitor.buildResult().schemaFileSpec
}

private class Visitor(
  private val packageName: String,
  private val defaultOutputFileName: String,
) : DotBaseVisitor<Unit>() {

  private lateinit var schemaFileSpec: FileSpec.Builder
  private lateinit var schemaTypeSpec: TypeSpec.Builder

  fun buildResult(): SchemaParseResult {
    return SchemaParseResult(
      schemaFileSpec = schemaFileSpec
        .addType(schemaTypeSpec.build())
        .build()
    )
  }

  override fun visitGraph(ctx: GraphContext) {
    val schemaFileName: String? = findGraphAttributeValue(ctx, "schemaFileName")
    schemaFileSpec = FileSpec.builder(
      packageName,
      schemaFileName ?: (ctx.id_()?.text?.let { "${it}Schema" }) ?: defaultOutputFileName
    )
    schemaTypeSpec = TypeSpec
      .classBuilder(name = (ctx.id_()?.text ?: "App") + "Schema")
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

    super.visitGraph(ctx)
  }

  private fun findGraphAttributeValue(ctx: GraphContext, name: String): String? {
    for (i in 0 until ctx.stmt_list().childCount) {
      if (ctx.stmt_list().stmt(i).id_(0)?.ID()?.text == name) {
        return ctx.stmt_list().stmt(i).id_(1)?.let {
          it.ID() ?: it.STRING()
        }?.text?.removeSurrounding("\"") ?: error("no value for graph attr '$name'")
      }
    }
    return null
  }
}

internal data class SchemaParseResult(
  val schemaFileSpec: FileSpec,
)
