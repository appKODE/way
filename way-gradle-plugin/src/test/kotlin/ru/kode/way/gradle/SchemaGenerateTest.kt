package ru.kode.way.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
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
  suspend fun runTest(testCase: TestCase) {
    val schema = File("src/test/resources/${testCase.schemaFile}")
    val expectedResults = testCase.expectedOutputFiles.map { File("src/test/resources/$it") }
    val outputDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
    buildSpecs(
      file = schema,
      config = createConfig()
    )
      .schemaFileSpec
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

  context("schema generation tests") {
    withData(
      TestCase(
        schemaFile = "single-flow.dot",
        expectedOutputFiles = listOf("single-flow-schema.kt"),
      ),
      TestCase(
        schemaFile = "schema-composition01.dot",
        expectedOutputFiles = listOf("schema-composition01.kt"),
      ),
      TestCase(
        schemaFile = "schema-composition02.dot",
        expectedOutputFiles = listOf("schema-composition02.kt"),
      ),
    ) { runTest(it) }
  }

  context("target generation tests") {
    withData(
      TestCase(
        schemaFile = "single-flow.dot",
        expectedOutputFiles = listOf("single-flow-targets.kt"),
      ),
      TestCase(
        schemaFile = "schema-composition01.dot",
        expectedOutputFiles = listOf("schema-composition01.kt"),
      ),
      TestCase(
        schemaFile = "schema-composition02.dot",
        expectedOutputFiles = listOf("schema-composition02.kt"),
      ),
    ) { runTest(it) }
  }
})

private fun createConfig() = CodeGenConfig(
  outputPackageName = SCHEME_GENERATION_PACKAGE,
  outputSchemaClassName = "DefaultTestNavSchema"
)

private const val SCHEME_GENERATION_PACKAGE = "ru.kode.test.app.scheme"

private data class TestCase(
  val schemaFile: String,
  val expectedOutputFiles: List<String>,
)
