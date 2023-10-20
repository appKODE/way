package ru.kode.way.gradle

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.buffer
import okio.source
import java.io.File
import java.nio.file.Files

suspend fun runTest(testCase: TestCase) {
  val schema = File("src/test/resources/${testCase.schemaFile}")
  val expectedResults = testCase.expectedOutputFiles.map { File("src/test/resources/$it") }
  val outputDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
  val config = createConfig()
  buildSpecs(
    file = schema,
    config = config,
    projectDir = File("."),
  ).apply {
    schemaFileSpec.writeTo(outputDirectory.toNioPath())
    targetsFileSpec.writeTo(outputDirectory.toNioPath())
    nodeBuilderSpecs.forEach { it.writeTo(outputDirectory.toNioPath()) }
  }
  expectedResults.forEach { expectedFile ->
    FileSystem.SYSTEM.apply {
      val outputFile = outputDirectory /
        config.outputPackageName.replace('.', '/').toPath(normalize = true) /
        expectedFile.name.removeSuffix(".txt").plus(".kt")
      source(outputFile).buffer().readUtf8() shouldBe
        source(expectedFile.toOkioPath()).buffer().readUtf8()
      withContext(Dispatchers.IO) {
        Files.delete(outputFile.toNioPath())
      }
    }
  }
}

private fun createConfig() = CodeGenConfig(
  outputPackageName = SCHEME_GENERATION_PACKAGE,
  outputSchemaClassName = "DefaultTestNavSchema"
)

const val SCHEME_GENERATION_PACKAGE = "ru.kode.test.app.schema"
