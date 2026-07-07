package ru.kode.way.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * Regression pins for malformed-but-real DOT syntax found VERBATIM in the consumer apps
 * (rosseti-android, prsv-android). Each fixture reproduces one offending construct minimally.
 *
 * These tests document the CURRENT branch behavior so the reworked [parseSchemaDotFile] cannot
 * silently change it again. Where the current behavior is a silent drop (not a desired feature),
 * the test says so explicitly — it pins released behavior, it does not endorse it.
 */
class MalformedDotToleranceTest :
  ShouldSpec({

    fun parse(name: String) = parseSchemaDotFile(File("src/test/resources/$name"), projectDir = File("."))

    // CASE 1 — prsv (main_flow.dot, shelf_book_categories_flow.dot):
    //   parameterName "categoryType"   <- NO '=' sign, bare quoted value.
    //
    // This appears VERBATIM in real prsv graphs. master's positional `chunked(2)` attribute parser
    // paired `parameterName` with the bare `"categoryType"` and accepted it. The branch's reworked
    // pairing loop initially required a literal `=`, DROPPED the bare attribute, orphaned the sibling
    // `parameterType`, and the both-or-neither validation HARD-THREW — a build-breaking regression on
    // real prsv files. Fixed in Parser.kt by pairing two adjacent ids positionally when there is no
    // `=`, restoring master's tolerance. This test locks that: the malformed attribute now parses.
    should("parameterName without '=' is tolerated and parsed positionally (prsv regression)") {
      val result = parse("malformed-param-name-no-equals.dot")
      val categoriesFlow = result.adjacencyList.keys.single { it.id == "categoriesFlow" }
      categoriesFlow.shouldNotBeNull()
      categoriesFlow as Node.Flow.Imported
      categoriesFlow.parameter shouldBe Parameter(name = "categoryType", type = "kotlin.String")
    }

    // CASE 2 — prsv (main_flow.dot bookFlow) + rosseti: a parameterType line with NO trailing
    // comma immediately followed by a resultType line. Both attributes must still be parsed.
    should("adjacent attributes without a comma separator are both parsed (prsv/rosseti regression)") {
      val result = parse("malformed-adjacent-attrs-no-comma.dot")
      val bookFlow = result.adjacencyList.keys.single { it.id == "bookFlow" }
      bookFlow.shouldNotBeNull()
      bookFlow as Node.Flow.Imported
      // parameterType (no trailing comma) AND the following resultType are both captured.
      bookFlow.parameter shouldBe Parameter(name = "config", type = "kotlin.String")
      bookFlow.resultType shouldBe "kotlin.Int"
    }

    // CASE 3 — rosseti (mapaddress_flow.dot): `result = "..."` used instead of `resultType`.
    // The unknown `result` key is silently ignored; resultType stays at its default kotlin.Unit.
    // This pins released behavior (silent ignore), it does not endorse the typo.
    should("unknown 'result' attribute is silently ignored, resultType stays default (rosseti regression)") {
      val result = parse("malformed-result-typo.dot")
      val mapAddressFlow = result.adjacencyList.keys.single { it.id == "mapAddressFlow" } as Node.Flow.Local
      mapAddressFlow.resultType shouldBe "kotlin.Unit"
      mapAddressFlow.parameter.shouldBeNull()
    }

    // CASE 4 — rosseti: a parent imports `instrumentDocumentDetailsFlow [type=schema]` but the
    // imported child .dot's ROOT node is named `documentDetailsFlow` (a different alias). The
    // registry is keyed by the child's real root-node id, so resolving by the parent's alias
    // returns null and the caller falls back to the owner's @file identity. Pin both facts.
    should(
      "import alias that differs from the child root name resolves to null (owner @file fallback) (rosseti regression)",
    ) {
      val child = parse("malformed-import-alias-mismatch-child.dot")
      val registry = SchemaRegistry.from(listOf(child))

      // Resolving by the parent's alias finds nothing — the registry only knows the real root name.
      registry.resolveSource("instrumentDocumentDetailsFlow").shouldBeNull()
      // The child's actual root name still resolves, confirming it IS registered.
      registry.resolveSource("documentDetailsFlow").shouldNotBeNull()
    }

    // CASE 5 — rosseti (main_flow.dot): the same edge `main -> shutdownScheduleFlow` declared on
    // two lines. The adjacency set de-duplicates, so the child appears exactly once and parsing
    // does not crash.
    should("a duplicate edge is de-duplicated and does not crash (rosseti regression)") {
      val result = parse("malformed-duplicate-edge.dot")
      val appFlow = result.adjacencyList.keys.single { it.id == "appFlow" }
      val children = result.adjacencyList[appFlow].orEmpty()
      children.map { it.id } shouldBe listOf("shutdownFlow")
    }
  })
