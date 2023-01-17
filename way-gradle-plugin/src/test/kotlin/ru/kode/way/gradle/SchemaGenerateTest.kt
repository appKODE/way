package ru.kode.way.gradle

import io.kotest.core.spec.style.ShouldSpec
import java.io.File

class SchemaGenerateTest : ShouldSpec({
  should("test") {
    val schema = File("src/test/resources/schema.dot")
    parseSchemeDotFile(schema)
  }
})
