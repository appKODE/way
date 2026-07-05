package ru.kode.way.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

class SchemaRegistryTest :
  ShouldSpec({

    should("resolveSource returns the single match when one schema has the requested root name") {
      val login = parseResult(
        filePath = "feature/login/way/login.dot",
        customPackage = "com.example.login",
        rootName = "loginFlow",
      )
      val registry = SchemaRegistry.from(listOf(login))

      val resolved = registry.resolveSource("loginFlow")

      resolved.shouldNotBeNull()
      resolved.filePath.toString() shouldBe Paths.get("feature/login/way/login.dot").toString()
    }

    should("resolveSource returns null when the root name is unknown") {
      val login = parseResult(
        filePath = "feature/login/way/login.dot",
        customPackage = "com.example.login",
        rootName = "loginFlow",
      )
      val registry = SchemaRegistry.from(listOf(login))

      registry.resolveSource("doesNotExist").shouldBeNull()
    }

    // Multiple .dot files in the same project may declare a same-named root (test fixtures
    // routinely do this; production may too across feature modules). Without a tiebreaker the
    // lookup is ambiguous and must return null.
    should("resolveSource returns null when multiple schemas share a root name and no importingPackage is supplied") {
      val moduleA = parseResult(
        filePath = "module-a/way/app.dot",
        customPackage = "com.example.a",
        rootName = "appFlow",
      )
      val moduleB = parseResult(
        filePath = "module-b/way/app.dot",
        customPackage = "com.example.b",
        rootName = "appFlow",
      )
      val registry = SchemaRegistry.from(listOf(moduleA, moduleB))

      registry.resolveSource("appFlow").shouldBeNull()
    }

    // Same-package preference: if exactly one of the ambiguous candidates is in the importer's
    // package, it wins. This is how cross-module imports stay unambiguous: each consumer module
    // imports its own copy.
    should("resolveSource picks the same-package candidate when importingPackage uniquely identifies one match") {
      val moduleA = parseResult(
        filePath = "module-a/way/app.dot",
        customPackage = "com.example.a",
        rootName = "appFlow",
      )
      val moduleB = parseResult(
        filePath = "module-b/way/app.dot",
        customPackage = "com.example.b",
        rootName = "appFlow",
      )
      val registry = SchemaRegistry.from(listOf(moduleA, moduleB))

      val resolved = registry.resolveSource("appFlow", importingPackage = "com.example.b")

      resolved.shouldNotBeNull()
      resolved.customPackage shouldBe "com.example.b"
    }

    // The importingPackage hint is a disambiguator, not a filter. If no candidate matches the
    // package, the lookup stays ambiguous and returns null — same as if the hint were absent.
    should("resolveSource returns null when importingPackage matches none of the candidates") {
      val moduleA = parseResult(
        filePath = "module-a/way/app.dot",
        customPackage = "com.example.a",
        rootName = "appFlow",
      )
      val moduleB = parseResult(
        filePath = "module-b/way/app.dot",
        customPackage = "com.example.b",
        rootName = "appFlow",
      )
      val registry = SchemaRegistry.from(listOf(moduleA, moduleB))

      registry.resolveSource("appFlow", importingPackage = "com.example.unrelated").shouldBeNull()
    }

    // Same-package preference does NOT promote a candidate when more than one candidate is in
    // the importer's package. Two co-located fixtures with the same root would still be
    // ambiguous and the caller must fall back to the owner's identity.
    should("resolveSource returns null when more than one same-package candidate exists") {
      val moduleA = parseResult(
        filePath = "module-a/way/app.dot",
        customPackage = "com.example.a",
        rootName = "appFlow",
      )
      val moduleAExtra = parseResult(
        filePath = "module-a/way/app_alt.dot",
        customPackage = "com.example.a",
        rootName = "appFlow",
      )
      val moduleB = parseResult(
        filePath = "module-b/way/app.dot",
        customPackage = "com.example.b",
        rootName = "appFlow",
      )
      val registry = SchemaRegistry.from(listOf(moduleA, moduleAExtra, moduleB))

      registry.resolveSource("appFlow", importingPackage = "com.example.a").shouldBeNull()
    }

    // Empty adjacency lists are skipped: SchemaRegistry.from must not crash on a parseResult
    // with no nodes (this can happen for stub fixtures or malformed inputs). They contribute
    // nothing to the registry index.
    should("from skips parseResults with an empty adjacency list") {
      val empty = parseResult(
        filePath = "empty/way/empty.dot",
        customPackage = "com.example.empty",
        rootName = null,
      )
      val real = parseResult(
        filePath = "feature/login/way/login.dot",
        customPackage = "com.example.login",
        rootName = "loginFlow",
      )
      val registry = SchemaRegistry.from(listOf(empty, real))

      registry.resolveSource("loginFlow").shouldNotBeNull()
    }
  })

private fun parseResult(filePath: String, customPackage: String, rootName: String?): SchemaParseResult {
  val adjacency: AdjacencyList = if (rootName == null) {
    emptyMap()
  } else {
    mapOf(Node.Flow.Local(id = rootName, resultType = "Unit", parameter = null) to emptyList())
  }
  return SchemaParseResult(
    filePath = Paths.get(filePath),
    graphId = null,
    customSchemaFileName = null,
    customSchemaClassName = null,
    customTargetsFileName = null,
    customPackage = customPackage,
    adjacencyList = adjacency,
  )
}
