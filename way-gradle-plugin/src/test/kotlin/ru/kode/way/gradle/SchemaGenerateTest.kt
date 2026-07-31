package ru.kode.way.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData

class SchemaGenerateTest :
  ShouldSpec({
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
        expectedOutputFiles = listOf("schema-parallel01.txt", "TestAppRegion.txt"),
        testName = "basic parallel flow schema",
      ),
      TestCase(
        schemaFile = "schema-parallel02.dot",
        expectedOutputFiles = listOf(
          "schema-parallel02.txt",
          "MainChildFinishRequest.txt",
          "OneChildFinishRequest.txt",
          "OneSchema.txt",
          // Region enum for the top-level parallel; the nested OneSchema also emits one
          // (`OneRegion.kt`) but its fixture coverage is left to schema-parallel01's enum
          // assertion since the generation logic is identical.
        ),
        testName = "multiple parallel in one schema",
      ),
      // These two fixtures only prove that two independently valid graphs codegen correctly and
      // differently from each other — they do NOT exercise resolveOverriddenDotFiles itself (no
      // source-set resolution happens here, each file is codegenned standalone). The actual
      // override-resolution behavior (picking the higher-priority file at a shared relative path,
      // including an end-to-end resolve+codegen assertion using these same two fixtures) is
      // covered in WayPluginSourceResolutionTest.
      TestCase(
        schemaFile = "flavor-override-base.dot",
        expectedOutputFiles = listOf("flavor-override-base-schema.txt"),
        testName = "codegen fixture: base graph (paired with the overriding-variant fixture below)",
      ),
      TestCase(
        schemaFile = "flavor-override-google.dot",
        expectedOutputFiles = listOf("flavor-override-google-schema.txt"),
        testName = "codegen fixture: overriding-variant graph produces different generated code than the base fixture",
      ),
    ) { runTest(it) }
  })
