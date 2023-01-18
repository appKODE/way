package ru.kode.way.gradle

import com.squareup.kotlinpoet.FileSpec
import java.io.File
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import ru.kode.way.gradle.DotParser.GraphContext

internal fun parseSchemeDotFile(
  file: File,
  packageName: String,
): FileSpec {
  val stream = CommonTokenStream(DotLexer(CharStreams.fromPath(file.toPath())))
  val parser = DotParser(stream)
  val parseTree = parser.graph()
  return Visitor(
    packageName = packageName,
    defaultOutputFileName = file.name,
  ).visitGraph(parseTree).build()
}

private class Visitor(
  private val packageName: String,
  private val defaultOutputFileName: String,
) : DotBaseVisitor<FileSpec.Builder>() {
  override fun visitGraph(ctx: DotParser.GraphContext): FileSpec.Builder {
    super.visitGraph(ctx)
    val schemeFileName: String? = findGraphAttributeValue(ctx, "schemeFileName")
    return FileSpec.builder(
      packageName,
      schemeFileName ?: ctx.id_()?.text ?: defaultOutputFileName
    )
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
