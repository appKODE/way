package ru.kode.way.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.WithDataTestName
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
    ).apply {
      schemaFileSpec.writeTo(outputDirectory.toNioPath())
      targetsFileSpec.writeTo(outputDirectory.toNioPath())
      nodeBuilderSpecs.forEach { it.writeTo(outputDirectory.toNioPath()) }
    }
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
        testName = "single flow schema",
      ),
      TestCase(
        schemaFile = "single-flow-attr-order.dot",
        expectedOutputFiles = listOf("single-flow-attr-order-schema.kt"),
        testName = "single flow schema with non default attr order",
      ),
      TestCase(
        schemaFile = "schema-composition01.dot",
        expectedOutputFiles = listOf("schema-composition01.kt"),
        testName = "schema composition: only schemas",
      ),
      TestCase(
        schemaFile = "schema-composition02.dot",
        expectedOutputFiles = listOf("schema-composition02.kt"),
        testName = "schema composition: schemas mixed with nodes",
      ),
    ) { runTest(it) }
  }

  context("target generation tests") {
    withData(
      TestCase(
        schemaFile = "targets-test01.dot",
        expectedOutputFiles = listOf("targets-test01-targets.kt"),
        testName = "multiple flows and screens",
      ),
      TestCase(
        schemaFile = "targets-test02.dot",
        expectedOutputFiles = listOf("targets-test02-targets.kt"),
        testName = "deeply nested flows",
      ),
    ) { runTest(it) }
  }

  context("node builder generation tests") {
    withData(
      TestCase(
        schemaFile = "node-builders-multiple-flows.dot",
        expectedOutputFiles = listOf(
          "Nb01appNodeBuilder.kt",
          "Nb01loginNodeBuilder.kt",
          "Nb01onboardingNodeBuilder.kt"
        ),
        testName = "multiple flows",
      ),
      TestCase(
        schemaFile = "node-builders-single-flow.dot",
        expectedOutputFiles = listOf(
          "Nb02appNodeBuilder.kt",
        ),
        testName = "single flow",
      ),
    ) { runTest(it) }
  }
})

private fun createConfig() = CodeGenConfig(
  outputPackageName = SCHEME_GENERATION_PACKAGE,
  outputSchemaClassName = "DefaultTestNavSchema"
)

private const val SCHEME_GENERATION_PACKAGE = "ru.kode.test.app.schema"

private data class TestCase(
  val schemaFile: String,
  val expectedOutputFiles: List<String>,
  val testName: String,
) : WithDataTestName {
  override fun dataTestName(): String {
    return testName
  }
}
