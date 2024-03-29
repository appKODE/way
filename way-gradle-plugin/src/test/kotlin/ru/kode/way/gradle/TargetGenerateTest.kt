package ru.kode.way.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData

class TargetGenerateTest : ShouldSpec({
  withData(
    TestCase(
      schemaFile = "targets-test01.dot",
      expectedOutputFiles = listOf("targets-test01-targets.txt"),
      testName = "multiple flows and screens",
    ),
    TestCase(
      schemaFile = "targets-test02.dot",
      expectedOutputFiles = listOf("targets-test02-targets.txt"),
      testName = "deeply nested flows",
    ),
    TestCase(
      schemaFile = "targets-test03.dot",
      expectedOutputFiles = listOf("targets-test03-targets.txt"),
      testName = "linked schema",
    ),
    TestCase(
      schemaFile = "targets-test04.dot",
      expectedOutputFiles = listOf("targets-test04-targets.txt"),
      testName = "targets with arguments",
    ),
  ) { runTest(it) }
})
