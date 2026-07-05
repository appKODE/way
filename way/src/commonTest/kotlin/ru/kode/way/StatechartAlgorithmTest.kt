package ru.kode.way

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

/**
 * Validates the schema-static SCXML building blocks in StatechartAlgorithm.kt against the semantics
 * of the W3C SCXML "Algorithm for SCXML Interpretation" (Appendix B). Uses hand-built [Path]s and a
 * `nodeTypeOf` lambda so the algorithm is exercised in isolation from [Schema]/codegen.
 *
 * Model used throughout (a tabbed app):
 *   app [Flow, root]
 *     tabs [ParallelFlow]
 *       home [Flow] → homeScreen [Screen], detail [Screen under home]
 *       explore [Flow] → exploreScreen [Screen]
 */
class StatechartAlgorithmTest : ShouldSpec() {
  init {
    val app = Path("app")
    val tabs = Path("app", "tabs")
    val home = Path("app", "tabs", "home")
    val homeScreen = Path("app", "tabs", "home", "homeScreen")
    val detail = Path("app", "tabs", "home", "detail")
    val explore = Path("app", "tabs", "explore")
    val exploreScreen = Path("app", "tabs", "explore", "exploreScreen")

    // Node-type lookup keyed on the last segment name of a path.
    val nodeTypeOf: (Path) -> Schema.NodeType = { path ->
      when (path.lastSegment().name) {
        "app", "home", "explore" -> Schema.NodeType.Flow
        "tabs" -> Schema.NodeType.ParallelFlow
        else -> Schema.NodeType.Screen
      }
    }

    context("getProperAncestors") {
      should("return ancestors nearest-first up to and including the root when boundary is null") {
        getProperAncestors(homeScreen, null) shouldBe listOf(home, tabs, app)
      }

      should("stop below the boundary (exclusive) when one is given") {
        getProperAncestors(homeScreen, tabs) shouldBe listOf(home)
      }

      should("return empty for a root path") {
        getProperAncestors(app, null) shouldBe emptyList()
      }

      should("return empty when boundary is the direct parent") {
        getProperAncestors(homeScreen, home) shouldBe emptyList()
      }
    }

    context("isProperDescendant") {
      should("hold for a strictly deeper path with the ancestor as prefix") {
        isProperDescendant(homeScreen, home) shouldBe true
        isProperDescendant(homeScreen, app) shouldBe true
      }
      should("be false for equal paths and for non-prefixes") {
        isProperDescendant(home, home) shouldBe false
        isProperDescendant(exploreScreen, home) shouldBe false
      }
    }

    context("findLCCA") {
      should("scope a within-region transition to the region's compound flow") {
        // homeScreen → detail both live under `home`: LCCA is `home`, so siblings (explore) are untouched.
        findLCCA(listOf(homeScreen, detail), nodeTypeOf) shouldBe home
      }

      should("lift past a parallel ancestor to the enclosing compound (parallel is not a valid LCCA)") {
        // A transition spanning two tabs. The nearest common ancestor is `tabs` (parallel), which
        // SCXML excludes, so the LCCA lifts to `app`.
        findLCCA(listOf(homeScreen, exploreScreen), nodeTypeOf) shouldBe app
      }

      should("return the root for a source+target that only share the root") {
        findLCCA(listOf(home, detail), nodeTypeOf) shouldBe app
      }
    }

    context("getTransitionDomain") {
      should("return null when there are no targets") {
        getTransitionDomain(homeScreen, emptyList(), isInternal = false, nodeTypeOf) shouldBe null
      }

      should("return the LCCA for an external transition") {
        // External transition home→detail exits `home` and re-enters it: domain is `home`'s parent scope `app`.
        getTransitionDomain(home, listOf(detail), isInternal = false, nodeTypeOf) shouldBe app
      }

      should("return the source for an internal transition whose targets are all descendants") {
        getTransitionDomain(home, listOf(detail), isInternal = true, nodeTypeOf) shouldBe home
      }

      should("fall back to LCCA for an internal transition when a target escapes the source") {
        getTransitionDomain(home, listOf(exploreScreen), isInternal = true, nodeTypeOf) shouldBe app
      }
    }

    context("computeExitSet") {
      should("return only configuration members below the domain, leaf-first") {
        val configuration = listOf(app, tabs, home, homeScreen, explore, exploreScreen)
        // Domain = home: only home's own descendants exit; the parallel sibling `explore` stays active.
        computeExitSet(home, configuration) shouldBe listOf(homeScreen)
      }

      should("exit an entire parallel subtree when the domain is the parallel's parent") {
        val configuration = listOf(app, tabs, home, homeScreen, explore, exploreScreen)
        // Domain = app: everything under app exits, deepest first.
        computeExitSet(app, configuration) shouldBe listOf(
          homeScreen,
          exploreScreen,
          home,
          explore,
          tabs,
        ).sortedByDescending { it.length }
      }

      should("be empty when nothing in the configuration is below the domain") {
        computeExitSet(home, listOf(app, tabs, explore, exploreScreen)) shouldBe emptyList()
      }
    }

    context("entryAncestors") {
      should("return the intermediate ancestors between target and domain, root-first") {
        // Entering homeScreen with domain `app`: fill in tabs then home (root-first / entry order).
        entryAncestors(homeScreen, app) shouldBe listOf(tabs, home)
      }

      should("be empty when the target is a direct child of the domain") {
        entryAncestors(detail, home) shouldBe emptyList()
      }
    }
  }
}
