package ru.kode.way.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData

class TargetGenerateTest : ShouldSpec({
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
})
