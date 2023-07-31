package ru.kode.way.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData

class SchemaGenerateTest : ShouldSpec({
  withData(
    TestCase(
      schemaFile = "single-flow.dot",
      expectedOutputFiles = listOf("single-flow-schema.txt"),
      testName = "single flow schema",
    ),
    TestCase(
      schemaFile = "single-flow-attr-order.dot",
      expectedOutputFiles = listOf("single-flow-attr-order-schema.txt"),
      testName = "single flow schema with non default attr order",
    ),
    TestCase(
      schemaFile = "single-flow-non-app-root.dot",
      expectedOutputFiles = listOf("single-flow-non-app-root-schema.txt"),
      testName = "single flow with non 'app' root node",
    ),
    TestCase(
      schemaFile = "schema-composition01.dot",
      expectedOutputFiles = listOf("schema-composition01.txt"),
      testName = "schema composition: only schemas",
    ),
    TestCase(
      schemaFile = "schema-composition02.dot",
      expectedOutputFiles = listOf("schema-composition02.txt"),
      testName = "schema composition: schemas mixed with nodes",
    ),
    TestCase(
      schemaFile = "schema-parallel01.dot",
      expectedOutputFiles = listOf("schema-parallel01.txt"),
      testName = "basic parallel flow schema",
    ),
    TestCase(
      schemaFile = "schema-parallel02.dot",
      expectedOutputFiles = listOf("schema-parallel02.txt"),
      testName = "multiple parallel in one schema",
    ),
  ) { runTest(it) }
})
