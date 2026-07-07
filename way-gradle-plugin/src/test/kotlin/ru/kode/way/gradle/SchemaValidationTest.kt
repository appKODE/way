package ru.kode.way.gradle

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Paths

class SchemaValidationTest :
  ShouldSpec({

    should("detect cycles in schema") {
      // validation-cycle.dot: screen1 -> screen2 -> screen1 creates a cycle
      val ex = shouldThrow<IllegalStateException> {
        parseSchemaDotFile(
          file = File("src/test/resources/validation-cycle.dot"),
          projectDir = File("."),
        )
      }
      ex.message!! shouldContain "cycle"
    }

    should("reject a flow with no children") {
      // validation-empty-flow.dot: 'app' is a root flow with no outgoing edges
      val ex = shouldThrow<IllegalStateException> {
        parseSchemaDotFile(
          file = File("src/test/resources/validation-empty-flow.dot"),
          projectDir = File("."),
        )
      }
      ex.message!! shouldContain "no children"
    }

    should("detect disconnected subgraph") {
      // validation-disconnected.dot: 'orphan' has no edges and is not reachable from 'app'
      val ex = shouldThrow<IllegalStateException> {
        parseSchemaDotFile(
          file = File("src/test/resources/validation-disconnected.dot"),
          projectDir = File("."),
        )
      }
      ex.message!! shouldContain "disconnected"
    }

    should("detect fan-in (node with multiple parents)") {
      // validation-fan-in.dot: 'shared' has two incoming edges from screen1 and screen2
      val ex = shouldThrow<IllegalStateException> {
        parseSchemaDotFile(
          file = File("src/test/resources/validation-fan-in.dot"),
          projectDir = File("."),
        )
      }
      ex.message!! shouldContain "fan-in"
    }

    should("reject a non-root parallel node with no children") {
      val ex = shouldThrow<IllegalStateException> {
        parseSchemaDotFile(
          file = File("src/test/resources/validation-empty-parallel.dot"),
          projectDir = File("."),
        )
      }
      ex.message!! shouldContain "no children"
    }

    should("reject a node with parameterName but no parameterType") {
      val ex = shouldThrow<IllegalStateException> {
        parseSchemaDotFile(
          file = File("src/test/resources/validation-half-param.dot"),
          projectDir = File("."),
        )
      }
      ex.message!! shouldContain "parameterName and parameterType"
    }

    should("reject a schema with multiple root flows") {
      val ex = shouldThrow<IllegalStateException> {
        parseSchemaDotFile(
          file = File("src/test/resources/validation-multi-root.dot"),
          projectDir = File("."),
        )
      }
      ex.message!! shouldContain "multiple root flows"
    }

    should("reject a node id containing a hyphen") {
      // validation-invalid-node-id.dot: "login-screen" uses a hyphen which is not a valid Kotlin identifier
      val ex = shouldThrow<IllegalStateException> {
        parseSchemaDotFile(
          file = File("src/test/resources/validation-invalid-node-id.dot"),
          projectDir = File("."),
        )
      }
      ex.message!! shouldContain "invalid node id"
    }

    should("reject a graph id containing a hyphen") {
      // validation-invalid-graph-id.dot: digraph "bad-graph" uses a hyphen which is not a valid Kotlin identifier
      val ex = shouldThrow<IllegalStateException> {
        parseSchemaDotFile(
          file = File("src/test/resources/validation-invalid-graph-id.dot"),
          projectDir = File("."),
        )
      }
      ex.message!! shouldContain "invalid graph id"
    }

    should("detect output file name collisions between two schema files") {
      val config = CodeGenConfig(outputPackageName = "com.example", outputSchemaClassName = "AppSchema")
      // Two results with graphId=null and no custom file names both resolve to config.outputSchemaClassName
      val result1 = SchemaParseResult(
        filePath = Paths.get("schema1.dot"),
        graphId = null,
        customSchemaFileName = null,
        customTargetsFileName = null,
        customPackage = null,
        adjacencyList = emptyMap(),
      )
      val result2 = SchemaParseResult(
        filePath = Paths.get("schema2.dot"),
        graphId = null,
        customSchemaFileName = null,
        customTargetsFileName = null,
        customPackage = null,
        adjacencyList = emptyMap(),
      )
      val ex = shouldThrow<IllegalStateException> {
        validateNoOutputFileCollisions(listOf(result1, result2), config)
      }
      ex.message!! shouldContain "AppSchema"
    }

    should("warn when screen and schema are siblings under the same flow") {
      // validation-sibling-screen-schema.dot: mainFlow -> main (screen) AND mainFlow -> chatFlow (schema).
      // Navigating to chatFlow dismisses main — the warning explains the alive-stack consequence and
      // suggests moving the edge to "main -> chatFlow" if stacking was the intent.
      val warnings = mutableListOf<String>()
      parseSchemaDotFile(
        file = File("src/test/resources/validation-sibling-screen-schema.dot"),
        projectDir = File("."),
        warn = warnings::add,
      )
      warnings.size shouldBe 1
      warnings[0] shouldContain "mainFlow"
      warnings[0] shouldContain "chatFlow"
      warnings[0] shouldContain "main"
      warnings[0] shouldContain "alive stack"
    }

    should("parse quoted node names correctly (H2 regression test)") {
      // validation-quoted-nodes.dot: node ids use double-quoted strings; the H2 fix ensures
      // id2.id_().asString() is called instead of id2.text so the surrounding quotes are stripped.
      val result = parseSchemaDotFile(
        file = File("src/test/resources/validation-quoted-nodes.dot"),
        projectDir = File("."),
      )
      val nodeIds = result.adjacencyList.keys.map { it.id }
      nodeIds shouldContain "myFlow"
      nodeIds shouldContain "myScreen"
      // 'myFlow' is a local flow with one child 'myScreen'
      val flow = result.adjacencyList.keys.single { it.id == "myFlow" }
      flow shouldBe Node.Flow.Local("myFlow", "kotlin.Unit", null)
      val children = result.adjacencyList[flow].orEmpty()
      children.map { it.id } shouldBe listOf("myScreen")
    }
  })
