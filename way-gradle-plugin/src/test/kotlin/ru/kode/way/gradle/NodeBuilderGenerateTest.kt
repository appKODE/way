package ru.kode.way.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData

class NodeBuilderGenerateTest : ShouldSpec({
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
    TestCase(
      schemaFile = "node-builders-parallel01.dot",
      expectedOutputFiles = listOf(
        "Nbp01mainNodeBuilder.kt",
        "Nbp01headNodeBuilder.kt",
        "Nbp01sheetNodeBuilder.kt"
      ),
      testName = "parallel node children in same schema",
    ),
    TestCase(
      schemaFile = "node-builders-parallel02.dot",
      expectedOutputFiles = listOf(
        "Nbp02mainNodeBuilder.kt",
      ),
      testName = "parallel node children are imported schemas",
    ),
  ) { runTest(it) }
})
