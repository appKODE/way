package ru.kode.way.gradle

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlin.io.path.createTempDirectory

class SchemaExtensionTest :
  ShouldSpec({

    val base = """
      digraph Home {
        package = "ru.kode.test.home"
        home [type = flow]
        home -> placeholder -> profile
      }
    """.trimIndent()

    val extension = """
      digraph Home {
        mode = "extend"
        purchase [parameterName = "url", parameterType = "kotlin.String"]
        placeholder -> purchase
      }
    """.trimIndent()

    fun withSourceSets(block: (mainDir: File, flavorDir: File) -> Unit) {
      val projectDir = createTempDirectory("way-extension").toFile()
      try {
        block(
          File(projectDir, "src/main/way").apply {
            mkdirs()
          },
          File(projectDir, "src/rustore/way").apply { mkdirs() },
        )
      } finally {
        projectDir.deleteRecursively()
      }
    }

    fun SchemaParseResult.childIds(nodeId: String): List<String> =
      adjacencyList.entries.single { (node, _) -> node.id == nodeId }.value.map { it.id }

    should("add nodes and edges of an extension to the schema with the same graph id") {
      withSourceSets { mainDir, flavorDir ->
        val baseFile = File(mainDir, "home.dot").apply { writeText(base) }
        val extensionFile = File(flavorDir, "home.dot").apply { writeText(extension) }

        val resolved = resolveOverriddenDotFiles(listOf(listOf(mainDir), listOf(flavorDir)))
        resolved shouldBe listOf(baseFile, extensionFile)

        val result = parseSchemaDotFiles(resolved, projectDir = mainDir.parentFile.parentFile.parentFile).single()
        result.childIds("placeholder") shouldContainExactlyInAnyOrder listOf("profile", "purchase")
        result.adjacencyList.keys.single { it.id == "purchase" } shouldBe
          Node.Screen("purchase", Parameter("url", "kotlin.String"))
        // segment ids are built from the file path: they must not depend on the flavor
        result.filePath.toString() shouldBe "src/main/way/home.dot"
        result.customPackage shouldBe "ru.kode.test.home"
      }
    }

    should("find the extended schema by graph id when the files are named differently") {
      withSourceSets { mainDir, flavorDir ->
        File(mainDir, "home.dot").writeText(base)
        File(flavorDir, "home_store.dot").writeText(extension)

        val resolved = resolveOverriddenDotFiles(listOf(listOf(mainDir), listOf(flavorDir)))

        parseSchemaDotFiles(resolved, projectDir = mainDir.parentFile.parentFile.parentFile).single()
          .childIds("placeholder") shouldContainExactlyInAnyOrder listOf("profile", "purchase")
      }
    }

    should("replace the schema together with its extensions by a higher-priority file without the mode") {
      withSourceSets { mainDir, flavorDir ->
        val variantDir = File(mainDir.parentFile.parentFile, "rustoreDebug/way").apply { mkdirs() }
        File(mainDir, "home.dot").writeText(base)
        File(flavorDir, "home.dot").writeText(extension)
        val replacement = File(variantDir, "home.dot").apply { writeText(base) }

        resolveOverriddenDotFiles(listOf(listOf(mainDir), listOf(flavorDir), listOf(variantDir))) shouldBe
          listOf(replacement)
      }
    }

    should("validate the extended schema as a whole") {
      withSourceSets { mainDir, flavorDir ->
        val files = listOf(
          File(mainDir, "home.dot").apply { writeText(base) },
          File(flavorDir, "home.dot").apply {
            writeText("digraph Home {\n  mode = \"extend\"\n  profile -> placeholder\n}")
          },
        )

        shouldThrow<IllegalStateException> {
          parseSchemaDotFiles(files, projectDir = mainDir.parentFile.parentFile.parentFile)
        }
          .message!! shouldContain "cycle"
      }
    }

    should("reject an extension which declares the type of a node of the extended schema anew") {
      withSourceSets { mainDir, flavorDir ->
        val files = listOf(
          File(mainDir, "home.dot").apply { writeText(base) },
          File(flavorDir, "home.dot").apply {
            writeText("digraph Home {\n  mode = \"extend\"\n  home [type = flow]\n}")
          },
        )

        shouldThrow<IllegalStateException> {
          parseSchemaDotFiles(files, projectDir = mainDir.parentFile.parentFile.parentFile)
        }
          .message!! shouldContain "duplicate node definition"
      }
    }

    should("reject an extension without a schema to extend") {
      withSourceSets { _, flavorDir ->
        val file = File(flavorDir, "home.dot").apply { writeText(extension) }

        shouldThrow<IllegalStateException> { parseSchemaDotFiles(listOf(file), projectDir = flavorDir) }
          .message!! shouldContain "there is no schema with this graph id"
      }
    }

    should("reject an extension when several schemas have its graph id") {
      withSourceSets { mainDir, flavorDir ->
        val files = listOf(
          File(mainDir, "home.dot").apply { writeText(base) },
          File(mainDir, "home2.dot").apply { writeText(base) },
          File(flavorDir, "home.dot").apply { writeText(extension) },
        )

        shouldThrow<IllegalStateException> {
          parseSchemaDotFiles(files, projectDir = mainDir.parentFile.parentFile.parentFile)
        }
          .message!! shouldContain "several schemas with this graph id"
      }
    }

    should("reject an extension which sets the package or the generated file names") {
      withSourceSets { mainDir, flavorDir ->
        val files = listOf(
          File(mainDir, "home.dot").apply { writeText(base) },
          File(flavorDir, "home.dot").apply {
            writeText(
              "digraph Home {\n  mode = \"extend\"\n  package = \"ru.kode.other\"\n  placeholder -> purchase\n}",
            )
          },
        )

        shouldThrow<IllegalStateException> {
          parseSchemaDotFiles(files, projectDir = mainDir.parentFile.parentFile.parentFile)
        }
          .message!! shouldContain "must not set \"package\""
      }
    }
  })
