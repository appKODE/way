package ru.kode.way.gradle

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File

internal fun parseSchemeDotFile(file: File) {
  val stream = CommonTokenStream(DotLexer(CharStreams.fromPath(file.toPath())))
  val parser = DotParser(stream)
  val parseTree = parser.graph()
  println(parseTree.toStringTree(parser))
}
