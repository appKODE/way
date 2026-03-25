package ru.kode.way.gradle

import io.kotest.engine.names.WithDataTestName

data class TestCase(val schemaFile: String, val expectedOutputFiles: List<String>, val testName: String) :
  WithDataTestName {
  override fun dataTestName(): String = testName
}
