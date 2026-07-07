package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import ru.kode.way.histapp.HistAppNodeBuilder
import ru.kode.way.histapp.HistoryParallelAppSchema
import ru.kode.way.histapp.histApp
import ru.kode.way.histapp.main.HistMainNodeBuilder
import ru.kode.way.histapp.main.HistTabANodeBuilder
import ru.kode.way.histapp.main.HistTabASchema
import ru.kode.way.histapp.main.HistTabBNodeBuilder
import ru.kode.way.histapp.main.HistTabBSchema
import ru.kode.way.histapp.main.HistoryParallelMainSchema
import ru.kode.way.histapp.main.histTabA
import ru.kode.way.histapp.main.histTabB

/**
 * Runtime coverage for SCXML deep-history restoration of a subtree that fans into PARALLEL regions.
 *
 * Fixture: a flow root `histApp` (history-parallel-app.dot) whose initial child is the plain screen
 * `histHome`, plus a lazily-mounted imported parallel-rooted schema `histMain`
 * (history-parallel-main.dot). `histMain` fans into two flow regions — `histTabA` (screens `histA1`
 * (initial) + `histA2`) and `histTabB` (screen `histB1`).
 *
 * Scenario: enter `histMain` (tabA→histA1, tabB→histB1 by default), drill tabA to histA2, then
 * navigate back to `histHome` — fully exiting the parallel and recording deep history for BOTH
 * regions at once ({histA2, histB1}). A `HistoryTarget(histMain, deep=true)` must then restore BOTH
 * regions to their own recorded leaves, not just one — the bug this fixes.
 */
class HistoryTargetParallelTest :
  ShouldSpec({

    val mainSchema = HistoryParallelMainSchema()
    val appSchema = HistoryParallelAppSchema(histMainSchema = mainSchema)

    // Absolute paths, assembled from the SAME schema instances the service sees so every Segment id
    // (with its @file disambiguator) matches the runtime region/node paths. AbsoluteTarget into a
    // parallel sub-region leaf is how the parallel is (re)entered; a bare parallel path is rejected.
    val rootSegment = appSchema.rootSegment
    val histMainSegment = appSchema.childSchemas.keys.first()
    val tabASegment = mainSchema.childSchemas.keys.first { it.name == "histTabA" }
    val histA1Segment = Target.histTabA.histA1.path.lastSegment()
    val histA2Segment = Target.histTabA.histA2.path.lastSegment()

    // The parallel `histMain`'s absolute path — the flow whose deep history we restore.
    val histMainPath = Path(listOf(rootSegment, histMainSegment))
    val histA1Path = Path(listOf(rootSegment, histMainSegment, tabASegment, histA1Segment))
    val histA2Path = Path(listOf(rootSegment, histMainSegment, tabASegment, histA2Segment))

    fun newService(): NavigationService<Unit> {
      val tabANodeBuilder = HistTabANodeBuilder(
        nodeFactory = object : HistTabANodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.histTabA.histA1)
          override fun createHistA1Node(): ScreenNode = TestScreenNode()
          override fun createHistA2Node(): ScreenNode = TestScreenNode()
        },
        schema = HistTabASchema(),
      )
      val tabBNodeBuilder = HistTabBNodeBuilder(
        nodeFactory = object : HistTabBNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.histTabB.histB1)
          override fun createHistB1Node(): ScreenNode = TestScreenNode()
        },
        schema = HistTabBSchema(),
      )
      val mainNodeBuilder = HistMainNodeBuilder(
        nodeFactory = object : HistMainNodeBuilder.Factory {
          override fun createRootNode(): ParallelFlowNode<*> = TestParallelNode()
          override fun createHistTabANodeBuilder(): NodeBuilder = tabANodeBuilder
          override fun createHistTabBNodeBuilder(): NodeBuilder = tabBNodeBuilder
        },
        schema = mainSchema,
      )
      // All navigation lives on the root flow `histApp`, an ancestor alive in BOTH parallel regions,
      // so each event reaches it by bubbling up regardless of which region currently holds focus.
      val appNodeBuilder = HistAppNodeBuilder(
        nodeFactory = object : HistAppNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.histApp.histHome,
            transitions = listOf(
              tr("enterMain", AbsoluteTarget(histA1Path)),
              tr("drillA2", AbsoluteTarget(histA2Path)),
              tr("toHome", Target.histApp.histHome),
              tr("histMainDeep", HistoryTarget(histMainPath, deep = true)),
              tr("histMainShallow", HistoryTarget(histMainPath, deep = false)),
            ),
          )
          override fun createHistMainNodeBuilder(): NodeBuilder = mainNodeBuilder
          override fun createHistHomeNode(): ScreenNode = TestScreenNode()
        },
        schema = appSchema,
      )
      return NavigationService(nodeBuilder = appNodeBuilder, onFinishRequest = { _: Unit -> Stay })
    }

    fun NavigationState.leafOf(regionName: String): String? = regionByName(regionName)?.active?.lastSegment()?.name

    should("deep HistoryTarget restores EVERY parallel region's recorded leaf") {
      val sut = newService()
      sut.collectTransitions().test {
        // Start on the plain home screen; the parallel is not yet mounted.
        awaitItem().leafOf("histApp") shouldBe "histHome"

        // Enter the parallel: both regions materialise at their defaults.
        sut.sendEvent(TestEvent("enterMain"))
        awaitItem().apply {
          leafOf("histTabA") shouldBe "histA1"
          leafOf("histTabB") shouldBe "histB1"
        }

        // Drill tabA to its NON-default screen; tabB stays at its default leaf.
        sut.sendEvent(TestEvent("drillA2"))
        awaitItem().apply {
          leafOf("histTabA") shouldBe "histA2"
          leafOf("histTabB") shouldBe "histB1"
        }

        // Fully exit the parallel back to home — records deep history {histA2, histB1} for histMain.
        sut.sendEvent(TestEvent("toHome"))
        awaitItem().apply {
          regionByName("histTabA") shouldBe null
          regionByName("histTabB") shouldBe null
          leafOf("histApp") shouldBe "histHome"
          // Recording guardrail: histMain accumulated BOTH sibling regions' atomic leaves (union),
          // not just whichever region was processed last.
          _history[histMainPath]?.map { it.lastSegment().name }?.toSet() shouldBe setOf("histA2", "histB1")
        }

        // Deep restore must bring BOTH regions back — tabA to its recorded histA2 AND tabB to histB1.
        sut.sendEvent(TestEvent("histMainDeep"))
        awaitItem().apply {
          leafOf("histTabA") shouldBe "histA2"
          leafOf("histTabB") shouldBe "histB1"
        }
      }
    }

    should("shallow HistoryTarget re-enters EVERY parallel region at its default, not just one") {
      val sut = newService()
      sut.collectTransitions().test {
        awaitItem().leafOf("histApp") shouldBe "histHome"

        sut.sendEvent(TestEvent("enterMain"))
        awaitItem().apply {
          leafOf("histTabA") shouldBe "histA1"
          leafOf("histTabB") shouldBe "histB1"
        }

        // Drill tabA to its NON-default screen so shallow-vs-deep can diverge.
        sut.sendEvent(TestEvent("drillA2"))
        awaitItem().leafOf("histTabA") shouldBe "histA2"

        // Fully exit the parallel — records {histA2, histB1} (recording is deep regardless of restore).
        sut.sendEvent(TestEvent("toHome"))
        awaitItem().apply {
          regionByName("histTabA") shouldBe null
          regionByName("histTabB") shouldBe null
        }

        // Shallow restore re-materialises the cold parallel and brings back BOTH regions, each at its
        // own DEFAULT — tabA forgets the histA2 drill (histA1), tabB at histB1. The old code restored
        // only the first region and dropped tabB entirely.
        sut.sendEvent(TestEvent("histMainShallow"))
        awaitItem().apply {
          leafOf("histTabA") shouldBe "histA1"
          leafOf("histTabB") shouldBe "histB1"
        }
      }
    }
  })
