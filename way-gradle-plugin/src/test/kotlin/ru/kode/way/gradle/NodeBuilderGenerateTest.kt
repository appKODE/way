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
  ) { runTest(it) }
})
