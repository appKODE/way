package ru.kode.way.gradle

import io.kotest.assertions.fail
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.buffer
import java.io.File
import java.nio.file.Files

class SchemaGenerateTest : ShouldSpec({
  should("generate single flow schema") {
    val testCaseName = "single-flow"
    val expectedOutputFiles = listOf("single-flow-schema.kt")
    val schema = File("src/test/resources/$testCaseName.dot")
    val expectedResults = expectedOutputFiles.map { File("src/test/resources/$it") }
    val outputDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
    parseSchemaDotFile(
      file = schema,
      packageName = SCHEME_GENERATION_PACKAGE,
    )
      .writeTo(outputDirectory.toNioPath())
    expectedResults.forEach { expectedFile ->
      FileSystem.SYSTEM.apply {
        val outputFile = outputDirectory /
          SCHEME_GENERATION_PACKAGE.replace('.', '/').toPath(normalize = true) / expectedFile.name
        source(outputFile).buffer().readUtf8() shouldBe
          source(expectedFile.toOkioPath()).buffer().readUtf8()
        withContext(Dispatchers.IO) {
          Files.delete(outputFile.toNioPath())
        }
      }
    }
  }

  should("schema-composition01") {
    fail("TODO")
  }

  should("schema-composition02") {
    fail("TODO")
  }
})

private const val SCHEME_GENERATION_PACKAGE = "ru.kode.test.app.scheme"
