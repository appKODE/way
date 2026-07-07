package ru.kode.way

import app.cash.turbine.test
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import ru.kode.way.acmesw.OuterRootChildFinishRequest
import ru.kode.way.acmesw.OuterRootNodeBuilder
import ru.kode.way.acmesw.ParallelTestAcmeSandwichSchema
import ru.kode.way.acmesw.home.HomeImportChildFinishRequest
import ru.kode.way.acmesw.home.HomeImportNodeBuilder
import ru.kode.way.acmesw.home.ParallelTestAcmeSandwichHomeSchema
import ru.kode.way.acmesw.home.taba.ParallelTestAcmeSandwichTabASchema
import ru.kode.way.acmesw.home.taba.TabANodeBuilder
import ru.kode.way.acmesw.home.taba.tabA
import ru.kode.way.acmesw.home.tabb.ParallelTestAcmeSandwichTabBSchema
import ru.kode.way.acmesw.home.tabb.TabBNodeBuilder
import ru.kode.way.acmesw.home.tabb.tabB
import ru.kode.way.acmesw.main.MainFlowImportNodeBuilder
import ru.kode.way.acmesw.main.ParallelTestAcmeSandwichMainSchema
import ru.kode.way.acmesw.main.mainFlowImport
import ru.kode.way.acmesw.sheet.ParallelTestAcmeSandwichSheetSchema
import ru.kode.way.acmesw.sheet.SiblingSheetChildFinishRequest
import ru.kode.way.acmesw.sheet.SiblingSheetNodeBuilder
import ru.kode.way.acmesw.sheet.install.InstallationImportNodeBuilder
import ru.kode.way.acmesw.sheet.install.ParallelTestAcmeSandwichInstallSchema
import ru.kode.way.acmesw.sheet.install.installationImport
import ru.kode.way.acmesw.sheet.siblingSheet
import ru.kode.way.acmetabs.AcmeAppFlowNodeBuilder
import ru.kode.way.acmetabs.AcmeAuthFlowNodeBuilder
import ru.kode.way.acmetabs.AcmeAuthFlowSchema
import ru.kode.way.acmetabs.AcmeExploreTabNodeBuilder
import ru.kode.way.acmetabs.AcmeExploreTabSchema
import ru.kode.way.acmetabs.AcmeHomeTabNodeBuilder
import ru.kode.way.acmetabs.AcmeHomeTabSchema
import ru.kode.way.acmetabs.AcmeMainFlowNodeBuilder
import ru.kode.way.acmetabs.AcmeMainFlowSchema
import ru.kode.way.acmetabs.AcmeTabsFlowNodeBuilder
import ru.kode.way.acmetabs.AcmeTabsFlowSchema
import ru.kode.way.acmetabs.ParallelTestAcmeTabsSchema
import ru.kode.way.acmetabs.acmeAuthFlow
import ru.kode.way.acmetabs.acmeExploreTab
import ru.kode.way.acmetabs.acmeHomeTab
import ru.kode.way.par01.Par01AppNodeBuilder
import ru.kode.way.par01.Parallel01Schema
import ru.kode.way.par01.bottom.Par01BottomNodeBuilder
import ru.kode.way.par01.bottom.Parallel01BottomSchema
import ru.kode.way.par01.bottom.par01Bottom
import ru.kode.way.par01.main.Par01MainChildFinishRequest
import ru.kode.way.par01.main.Par01MainNodeBuilder
import ru.kode.way.par01.main.Parallel01MainSchema
import ru.kode.way.par01.par01App
import ru.kode.way.par01.top.Par01TopNodeBuilder
import ru.kode.way.par01.top.Parallel01TopSchema
import ru.kode.way.par01.top.par01Top
import ru.kode.way.par02.Par02AppNodeBuilder
import ru.kode.way.par02.Parallel02Schema
import ru.kode.way.par02.alpha.Par02AlphaNodeBuilder
import ru.kode.way.par02.alpha.Parallel02AlphaSchema
import ru.kode.way.par02.alpha.par02Alpha
import ru.kode.way.par02.beta.Par02BetaNodeBuilder
import ru.kode.way.par02.beta.Parallel02BetaSchema
import ru.kode.way.par02.beta.par02Beta
import ru.kode.way.par02.main.Par02MainChildFinishRequest
import ru.kode.way.par02.main.Par02MainNodeBuilder
import ru.kode.way.par02.main.Parallel02MainSchema
import ru.kode.way.par02.par02App
import ru.kode.way.par03.Par03AppNodeBuilder
import ru.kode.way.par03.Parallel03Schema
import ru.kode.way.par03.alpha.Par03AlphaNodeBuilder
import ru.kode.way.par03.alpha.Parallel03AlphaSchema
import ru.kode.way.par03.alpha.par03Alpha
import ru.kode.way.par03.beta.Par03BetaNodeBuilder
import ru.kode.way.par03.beta.Parallel03BetaSchema
import ru.kode.way.par03.beta.par03Beta
import ru.kode.way.par03.main.Par03MainNodeBuilder
import ru.kode.way.par03.main.Parallel03MainSchema
import ru.kode.way.par03.par03App
import ru.kode.way.par04.Par04AppNodeBuilder
import ru.kode.way.par04.Parallel04Schema
import ru.kode.way.par04.alpha.Par04AlphaNodeBuilder
import ru.kode.way.par04.alpha.Parallel04AlphaSchema
import ru.kode.way.par04.beta.Par04BetaNodeBuilder
import ru.kode.way.par04.beta.Parallel04BetaSchema
import ru.kode.way.par04.beta.par04Beta
import ru.kode.way.par04.innera.Par04InnerANodeBuilder
import ru.kode.way.par04.innera.Parallel04InnerASchema
import ru.kode.way.par04.innera.par04InnerA
import ru.kode.way.par04.innerb.Par04InnerBNodeBuilder
import ru.kode.way.par04.innerb.Parallel04InnerBSchema
import ru.kode.way.par04.innerb.par04InnerB
import ru.kode.way.par04.main.Par04MainNodeBuilder
import ru.kode.way.par04.main.Parallel04MainSchema
import ru.kode.way.par04.par04App
import ru.kode.way.par05.Par05AppNodeBuilder
import ru.kode.way.par05.Parallel05Schema
import ru.kode.way.par05.main.Par05AlphaNodeBuilder
import ru.kode.way.par05.main.Par05AlphaSchema
import ru.kode.way.par05.main.Par05BetaNodeBuilder
import ru.kode.way.par05.main.Par05BetaSchema
import ru.kode.way.par05.main.Par05MainChildFinishRequest
import ru.kode.way.par05.main.Par05MainNodeBuilder
import ru.kode.way.par05.main.Parallel05MainSchema
import ru.kode.way.par05.main.par05Alpha
import ru.kode.way.par05.main.par05Beta
import ru.kode.way.par05.par05App
import ru.kode.way.par06.Par06AppNodeBuilder
import ru.kode.way.par06.Parallel06Schema
import ru.kode.way.par06.main.Par06AlphaChildFinishRequest
import ru.kode.way.par06.main.Par06AlphaNodeBuilder
import ru.kode.way.par06.main.Par06AlphaSchema
import ru.kode.way.par06.main.Par06BetaNodeBuilder
import ru.kode.way.par06.main.Par06BetaSchema
import ru.kode.way.par06.main.Par06InnerANodeBuilder
import ru.kode.way.par06.main.Par06InnerASchema
import ru.kode.way.par06.main.Par06InnerBNodeBuilder
import ru.kode.way.par06.main.Par06InnerBSchema
import ru.kode.way.par06.main.Par06MainChildFinishRequest
import ru.kode.way.par06.main.Par06MainNodeBuilder
import ru.kode.way.par06.main.Parallel06MainSchema
import ru.kode.way.par06.main.par06Beta
import ru.kode.way.par06.main.par06InnerA
import ru.kode.way.par06.main.par06InnerB
import ru.kode.way.par06.par06App
import ru.kode.way.par06m.Par06mAppNodeBuilder
import ru.kode.way.par06m.Parallel06MixedSchema
import ru.kode.way.par06m.alpha.Par06mAlphaChildFinishRequest
import ru.kode.way.par06m.alpha.Par06mAlphaNodeBuilder
import ru.kode.way.par06m.alpha.Par06mInnerANodeBuilder
import ru.kode.way.par06m.alpha.Par06mInnerASchema
import ru.kode.way.par06m.alpha.Par06mInnerBNodeBuilder
import ru.kode.way.par06m.alpha.Par06mInnerBSchema
import ru.kode.way.par06m.alpha.Parallel06MixedAlphaSchema
import ru.kode.way.par06m.alpha.par06mInnerA
import ru.kode.way.par06m.alpha.par06mInnerB
import ru.kode.way.par06m.main.Par06mBetaNodeBuilder
import ru.kode.way.par06m.main.Par06mBetaSchema
import ru.kode.way.par06m.main.Par06mMainChildFinishRequest
import ru.kode.way.par06m.main.Par06mMainNodeBuilder
import ru.kode.way.par06m.main.Parallel06MixedMainSchema
import ru.kode.way.par06m.main.par06mBeta
import ru.kode.way.par06m.par06mApp
import ru.kode.way.paramsw.ParallelTestParamswSchema
import ru.kode.way.paramsw.ParamAppRootNodeBuilder
import ru.kode.way.paramsw.home.ParallelTestParamswHomeSchema
import ru.kode.way.paramsw.home.ParamHomeImportNodeBuilder
import ru.kode.way.paramsw.home.taba.ParallelTestParamswTabASchema
import ru.kode.way.paramsw.home.taba.ParamTabANodeBuilder
import ru.kode.way.paramsw.home.taba.paramTabA
import ru.kode.way.paramsw.home.tabb.ParallelTestParamswTabBSchema
import ru.kode.way.paramsw.home.tabb.ParamTabBNodeBuilder
import ru.kode.way.paramsw.home.tabb.paramTabB
import ru.kode.way.paramsw.main.ParallelTestParamswMainSchema
import ru.kode.way.paramsw.main.ParamMainImportNodeBuilder
import ru.kode.way.paramsw.main.paramMainImport
import ru.kode.way.paramsw.sheet.ParallelTestParamswSheetSchema
import ru.kode.way.paramsw.sheet.ParamSheetImportNodeBuilder
import ru.kode.way.paramsw.sheet.paramSheetImport
import ru.kode.way.parcri.ParallelTestCrossRegionIntermediateSchema
import ru.kode.way.parcri.ParcriAlphaNodeBuilder
import ru.kode.way.parcri.ParcriAlphaSchema
import ru.kode.way.parcri.ParcriBetaNodeBuilder
import ru.kode.way.parcri.ParcriBetaSchema
import ru.kode.way.parcri.ParcriRootNodeBuilder
import ru.kode.way.parcri.`inner`.ParallelTestCrossRegionIntermediateInnerSchema
import ru.kode.way.parcri.`inner`.ParcriBetaImportedNodeBuilder
import ru.kode.way.parcri.`inner`.ParcriBetaLeftNodeBuilder
import ru.kode.way.parcri.`inner`.ParcriBetaLeftSchema
import ru.kode.way.parcri.`inner`.ParcriBetaRightNodeBuilder
import ru.kode.way.parcri.`inner`.ParcriBetaRightSchema
import ru.kode.way.parcri.`inner`.parcriBetaLeft
import ru.kode.way.parcri.`inner`.parcriBetaRight
import ru.kode.way.parfm.ParallelFlatMixSchema
import ru.kode.way.parfm.ParfmAppNodeBuilder
import ru.kode.way.parfm.alpha.ParallelFlatMixAlphaSchema
import ru.kode.way.parfm.alpha.ParfmAlphaNodeBuilder
import ru.kode.way.parfm.alpha.parfmAlpha
import ru.kode.way.parfm.main.ParallelFlatMixMainSchema
import ru.kode.way.parfm.main.ParfmBetaNodeBuilder
import ru.kode.way.parfm.main.ParfmBetaSchema
import ru.kode.way.parfm.main.ParfmMainChildFinishRequest
import ru.kode.way.parfm.main.ParfmMainNodeBuilder
import ru.kode.way.parfm.main.parfmBeta
import ru.kode.way.parfm.parfmApp
import ru.kode.way.parlazyint.OuterAppNodeBuilder
import ru.kode.way.parlazyint.ParallelTestLazyIntermediateSchema
import ru.kode.way.parlazyint.inner.ImportedParallelNodeBuilder
import ru.kode.way.parlazyint.inner.LeftTabNodeBuilder
import ru.kode.way.parlazyint.inner.LeftTabSchema
import ru.kode.way.parlazyint.inner.ParallelTestLazyIntermediateInnerSchema
import ru.kode.way.parlazyint.inner.RightTabNodeBuilder
import ru.kode.way.parlazyint.inner.RightTabSchema
import ru.kode.way.parlazyint.inner.leftTab
import ru.kode.way.parlazyint.inner.rightTab
import ru.kode.way.parlazyint.outerApp
import ru.kode.way.parnested.NestedOuterChildFinishRequest
import ru.kode.way.parnested.NestedOuterNodeBuilder
import ru.kode.way.parnested.ParallelTestNestedRootSchema
import ru.kode.way.parnested.nested.NestedAlphaNodeBuilder
import ru.kode.way.parnested.nested.NestedAlphaSchema
import ru.kode.way.parnested.nested.NestedBetaNodeBuilder
import ru.kode.way.parnested.nested.NestedBetaSchema
import ru.kode.way.parnested.nested.NestedInnerChildFinishRequest
import ru.kode.way.parnested.nested.NestedInnerNodeBuilder
import ru.kode.way.parnested.nested.ParallelTestNestedInnerSchema
import ru.kode.way.parnested.nested.nestedAlpha
import ru.kode.way.parnested.nested.nestedBeta
import ru.kode.way.parrelfocused.ParallelTestRelFocusedSchema
import ru.kode.way.parrelfocused.ParrelfRootNodeBuilder
import ru.kode.way.parrelfocused.alpha.ParallelTestRelFocusedAlphaSchema
import ru.kode.way.parrelfocused.alpha.ParrelfAlphaNodeBuilder
import ru.kode.way.parrelfocused.alpha.`inner`.ParallelTestRelFocusedAlphaInnerSchema
import ru.kode.way.parrelfocused.alpha.`inner`.ParrelfAlphaInnerNodeBuilder
import ru.kode.way.parrelfocused.alpha.`inner`.parrelfAlphaInner
import ru.kode.way.parrelfocused.alpha.parrelfAlpha
import ru.kode.way.parrelfocused.beta.ParallelTestRelFocusedBetaSchema
import ru.kode.way.parrelfocused.beta.ParrelfBetaNodeBuilder
import ru.kode.way.parrelfocused.beta.`inner`.ParallelTestRelFocusedBetaInnerSchema
import ru.kode.way.parrelfocused.beta.`inner`.ParrelfBetaInnerNodeBuilder
import ru.kode.way.parrelfocused.beta.`inner`.parrelfBetaInner
import ru.kode.way.parrelfocused.beta.parrelfBeta
import ru.kode.way.partoproot.AlphaNodeBuilder
import ru.kode.way.partoproot.AlphaSchema
import ru.kode.way.partoproot.BetaNodeBuilder
import ru.kode.way.partoproot.BetaSchema
import ru.kode.way.partoproot.ParallelTestTopRootSchema
import ru.kode.way.partoproot.TopRootNodeBuilder
import ru.kode.way.partoproot.alpha
import ru.kode.way.partoproot.beta
import ru.kode.way.partoprootfinish.ChildFinishNodeBuilder
import ru.kode.way.partoprootfinish.ChildFinishSchema
import ru.kode.way.partoprootfinish.ChildOtherNodeBuilder
import ru.kode.way.partoprootfinish.ChildOtherSchema
import ru.kode.way.partoprootfinish.ParallelTestTopRootFinishSchema
import ru.kode.way.partoprootfinish.RootParallelChildFinishRequest
import ru.kode.way.partoprootfinish.RootParallelNodeBuilder
import ru.kode.way.partoprootfinish.childFinish
import ru.kode.way.partoprootfinish.childOther

class ParallelNodeTest : ShouldSpec() {
  init {
    should("resolve initial state with basic parallel setup") {
      val sut = buildPar01Service()

      sut.collectTransitions().test {
        awaitItem().apply {
          regions.keys.map { it.path.toString() }.shouldContainOnly(
            "par01App",
            "par01App.par01Main.par01Top",
            "par01App.par01Main.par01Bottom",
          )
          val appRegion = regions.entries.find { it.key.path.lastSegment().name == "par01App" }?.value
          appRegion?.alive.orEmpty().map { it.toString() }
            .shouldContainInOrder(
              "par01App",
              "par01App.par01Main",
            )
          appRegion?.active?.toString() shouldBe "par01App.par01Main"

          val topRegion = regions.entries.find { it.key.path.lastSegment().name == "par01Top" }?.value
          topRegion?.alive.orEmpty().map { it.toString() }
            .shouldContainInOrder(
              "par01App.par01Main.par01Top",
              "par01App.par01Main.par01Top.par01TopIntro",
            )
          topRegion?.active?.toString() shouldBe "par01App.par01Main.par01Top.par01TopIntro"

          val bottomRegion = regions.entries.find { it.key.path.lastSegment().name == "par01Bottom" }?.value
          bottomRegion?.alive.orEmpty().map { it.toString() }
            .shouldContainInOrder(
              "par01App.par01Main.par01Bottom",
              "par01App.par01Main.par01Bottom.par01BottomMain",
            )
          bottomRegion?.active?.toString() shouldBe "par01App.par01Main.par01Bottom.par01BottomMain"
        }
      }
    }

    should("clamp sub-region alive to own scope") {
      val sut = buildPar01Service()

      sut.collectTransitions().test {
        awaitItem().apply {
          val topRegion = regions.entries.find { it.key.path.lastSegment().name == "par01Top" }?.value
          topRegion?.alive.orEmpty().map { it.toString() }.let { alive ->
            alive.none { it == "par01App" } shouldBe true
            alive.none { it == "par01App.par01Main" } shouldBe true
          }
        }
      }
    }

    should("deepestRegion falls back to the deepest active sub-region (the null-DispatchBackTo default)") {
      val regionB = RegionId(Path("regionB"))
      // With no DispatchBackTo override (transition(Back) returns Ignore) Back routes to the deepest.
      deepestRegion(
        mapOf(
          RegionId(Path("regionA")) to Path("regionA", "screen1"),
          regionB to Path("regionB", "flow", "screen2"), // deeper
        ),
      ) shouldBe regionB
    }

    should("resolveRegionId picks the named region even when another region is deeper") {
      // DispatchBackTo(regionB) must route Back to regionB regardless of regionA being deeper —
      // resolveRegionId resolves the id and Back goes there, not to the deepest.
      val regionB = RegionId(Path("regionB"))
      resolveRegionId(
        regionB,
        setOf(RegionId(Path("regionA")), regionB),
      ) shouldBe regionB
    }

    should("stale DispatchBackTo region soft-falls-back to the deepest active sub-region (Back never throws)") {
      val staleId = RegionId(Path("stale"))
      val alpha = RegionId(Path("alpha"))
      val gamma = RegionId(Path("gamma"))
      val subRegions = mapOf(
        alpha to Path("alpha", "screenA"),
        gamma to Path("gamma", "flow", "screenC"), // deeper
      )
      // A stale id resolves to nothing…
      resolveRegionId(staleId, subRegions.keys) shouldBe null
      // …so Back soft-falls-back to the deepest active sub-region rather than throwing.
      deepestRegion(subRegions) shouldBe gamma
    }

    should("resolveRegionId resolves schema-local region id via suffix match against absolute activePaths") {
      // Leaf modules' generated *RegionId constants encode the path under their own schema only
      // (e.g. Path(homeFlow, exploreFlow)). The runtime stores parallel sub-regions under the
      // absolute path that includes the parent flow's mount (Path(appFlow, homeFlow, exploreFlow)).
      // Suffix-match resolves the schema-local id to the absolute key.
      val schemaLocalRegion = RegionId(Path("homeFlow", "exploreFlow"))
      val absoluteExplore = RegionId(Path("appFlow", "homeFlow", "exploreFlow"))
      val absoluteMyAcme = RegionId(Path("appFlow", "homeFlow", "myAcmeFlow"))
      resolveRegionId(
        schemaLocalRegion,
        setOf(absoluteExplore, absoluteMyAcme),
      ) shouldBe absoluteExplore
    }

    should("resolveRegionId resolves schema-local region id across cross-module @file boundary") {
      // Cross-module schema imports: the parent's Gradle codegen has no visibility of the leaf
      // module's `.dot` file, so it stamps its own `@graphId:file` on the boundary segment
      // while the leaf module's codegen stamps the leaf's. `endsWith` (strict full-id) fails on
      // that one segment; the boundary-tolerant tier in `resolveRegionId` matches it by
      // name while still requiring every deeper segment to equal by full id.
      val schemaLocalRegion = RegionId(
        Path(
          listOf(
            Segment("homeFlow@HomeFlow:home_flow.dot"),
            Segment("exploreFlow@HomeFlow:home_flow.dot"),
          ),
        ),
      )
      val absExplore = RegionId(
        Path(
          listOf(
            Segment("appFlow@AppFlow:app_flow.dot"),
            Segment("mainFlow@MainFlow:main_flow.dot"),
            Segment("homeFlow@MainFlow:main_flow.dot"), // parent's @file at boundary
            Segment("exploreFlow@HomeFlow:home_flow.dot"), // deep segment matches strictly
          ),
        ),
      )
      val absMyAcme = RegionId(
        Path(
          listOf(
            Segment("appFlow@AppFlow:app_flow.dot"),
            Segment("mainFlow@MainFlow:main_flow.dot"),
            Segment("homeFlow@MainFlow:main_flow.dot"),
            Segment("myAcmeFlow@HomeFlow:home_flow.dot"),
          ),
        ),
      )
      resolveRegionId(schemaLocalRegion, setOf(absExplore, absMyAcme)) shouldBe absExplore
    }

    should(
      "resolveRegionId refuses to match when a deep segment's @file differs (boundary-tolerance is strict beyond segment 0)",
    ) {
      // Lock the strict-at-non-boundary guarantee: a schema-local id whose deeper segment has
      // a different `@file` than every candidate must NOT match. Only the FIRST segment
      // (schema-root boundary) is allowed name-only tolerance. An unresolved id means Back
      // soft-falls-back to the deepest region rather than misrouting.
      val mismatchedDeep = RegionId(
        Path(
          listOf(
            Segment("homeFlow@HomeFlow:home_flow.dot"),
            Segment("exploreFlow@WRONG:wrong.dot"), // deep segment with wrong @file
          ),
        ),
      )
      val candidate = RegionId(
        Path(
          listOf(
            Segment("appFlow@AppFlow:app_flow.dot"),
            Segment("homeFlow@MainFlow:main_flow.dot"),
            Segment("exploreFlow@HomeFlow:home_flow.dot"),
          ),
        ),
      )
      resolveRegionId(mismatchedDeep, setOf(candidate)) shouldBe null
    }

    // Regression — real-world usage. `MainFlow.Home(targetTab, followUpEvents=List<Event>)` folds N
    // follow-ups into a chain `acc thenEnqueue ev` (MainFlowNode.kt:175-176). The single-thenEnqueue
    // test below only proves N=1 works; this asserts N=3 dispatches every event in order, that
    // payload-carrying events keep their data, and that the runtime delivers them to the right
    // region.
    should("NavigateTo thenEnqueue chain of N≥3 dispatches every follow-up in order with payloads intact") {
      data class TagEvent(val tag: String, val value: Int) : Event

      val mainNode = object : ParallelFlowNode<Unit>() {
        override val dismissResult = Unit
        override fun transition(event: Event): FlowTransition<Unit> = Ignore
      }
      val betaReceived = mutableListOf<TagEvent>()
      val followUps = listOf(TagEvent("first", 1), TagEvent("second", 2), TagEvent("third", 3))
      // Mirrors a real-world app's fold idiom (`acc thenEnqueue ev`). MUST use the
      // operator chain — that's the contract being locked. Manually constructing
      // NavigateAndEnqueue with the events list would bypass thenEnqueue and miss regressions
      // where the operator drops history.
      val chained = followUps.drop(1).fold(
        NavigateTo(Target.par02App.par02Main) thenEnqueue followUps.first(),
      ) { acc, ev -> acc thenEnqueue ev }

      val sut = buildPar02Service(
        createMainNode = { mainNode },
        appTransitions = listOf(
          TestFlowTransitionSpec(
            eventMatcher = { it is TestEvent && it.name == "trigger" },
            transition = chained,
          ),
        ),
        betaTransitions = listOf(
          TestFlowTransitionSpec(
            eventMatcher = {
              if (it is TagEvent) {
                betaReceived.add(it)
                true
              } else {
                false
              }
            },
            transition = Stay,
          ),
        ),
      )

      sut.collectTransitions().test {
        awaitItem() // initial
        sut.sendEvent(TestEvent("trigger"))
        // Drain transitions until all follow-ups have been delivered. Each event in the chain
        // produces at least one transition emission; awaitItem() repeatedly until quiet.
        repeat(followUps.size + 1) { runCatching { awaitItem() } }
        betaReceived shouldBe followUps
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("NavigateTo thenEnqueue enqueues follow-up events after the navigation resolves") {
      // Replaces the assisted-injection PostEntryParams pattern: navigate AND queue an event
      // that the destination flow handles via its normal transition function.
      val mainNode = object : ParallelFlowNode<Unit>() {
        override val dismissResult = Unit
        override fun transition(event: Event): FlowTransition<Unit> = Ignore
      }
      val betaReceivedEvents = mutableListOf<Event>()
      val sut = buildPar02Service(
        createMainNode = { mainNode },
        appTransitions = listOf(
          TestFlowTransitionSpec(
            eventMatcher = { it is TestEvent && it.name == "trigger" },
            transition = NavigateTo(Target.par02App.par02Main) thenEnqueue TestEvent("followUp"),
          ),
        ),
        betaTransitions = listOf(
          TestFlowTransitionSpec(
            eventMatcher = {
              if (it is TestEvent && it.name == "followUp") {
                betaReceivedEvents.add(it)
                true
              } else {
                false
              }
            },
            transition = Stay,
          ),
        ),
      )
      sut.collectTransitions().test {
        awaitItem()
        sut.sendEvent(TestEvent("trigger"))
        awaitItem()
        // After the navigation resolved, the runtime drained the queued TestEvent("followUp")
        // and it reached beta's transition.
        betaReceivedEvents.size shouldBe 1
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("resolveRegionId preserves strict-equality fast path for an absolute region id") {
      val absolute = RegionId(Path("appFlow", "homeFlow", "myAcmeFlow"))
      resolveRegionId(
        absolute,
        setOf(RegionId(Path("appFlow", "homeFlow", "exploreFlow")), absolute),
      ) shouldBe absolute
    }

    should("DispatchBackTo routes Back to the named region even when a sibling has a deeper stack") {
      // Alpha navigates to screen2 (becomes deeper). The parallel returns DispatchBackTo(beta)
      // (shallower). Back must go to beta's flow root → Finish(Unit) → child-finish event at the
      // parallel — NOT to alpha (which deepest would have picked). We distinguish by checking
      // alpha's active path is unchanged after Back.
      val receivedEvents = mutableListOf<Event>()
      var betaRegionId: RegionId? = null
      val mainNode = object : ParallelFlowNode<Unit>() {
        override val dismissResult = Unit
        override fun transition(event: Event): FlowTransition<Unit> {
          receivedEvents.add(event)
          return if (event == Event.Back) {
            DispatchBackTo(requireNotNull(betaRegionId) { "capture betaRegionId before sending Back" })
          } else {
            Ignore
          }
        }
      }
      val sut = buildPar02Service(
        alphaTransitions = listOf(tr("goToScreen2", Target.par02Alpha.par02AlphaScreen2)),
        createMainNode = { mainNode },
        appTransitions = listOf(
          TestFlowTransitionSpec(
            eventMatcher = { it is Par02MainChildFinishRequest.Par02Beta },
            transition = Stay,
          ),
        ),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val alphaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par02Alpha" }
        betaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par02Beta" }

        // Make alpha deeper than beta
        sut.sendEvent(TestEvent("goToScreen2"))
        awaitItem().apply {
          regions[alphaRegionId]!!.active.lastSegment().name shouldBe "par02AlphaScreen2"
        }

        sut.sendEvent(Event.Back)
        awaitItem() // Back → beta chosen → Finish(Unit) → EnqueueEvent(Par02MainChildFinishRequest.Par02Beta)
        awaitItem().apply {
          // child-finish event → parallel Ignore → app Stay
          // DispatchBackTo picked beta (not alpha), so alpha is still at screen2
          regions[alphaRegionId]!!.active.lastSegment().name shouldBe "par02AlphaScreen2"
          regions[betaRegionId]!!.active.lastSegment().name shouldBe "par02BetaScreen1"
        }
        cancelAndIgnoreRemainingEvents()
      }

      // Parallel received the beta child-finish request, proving DispatchBackTo chose beta
      receivedEvents.any { it is Par02MainChildFinishRequest.Par02Beta } shouldBe true
    }

    should("L1 fix: DispatchBackTo triggers Finish (not wrong NavigateTo) at the targeted flow root") {
      // The parallel returns DispatchBackTo(top). Back at top's only screen must Finish the top
      // flow, not produce a NavigateTo(flowNode) which would be an invalid active path.
      val receivedEvents = mutableListOf<Event>()
      val topRegion = Parallel01MainSchema(Parallel01TopSchema(), Parallel01BottomSchema()).par01TopRegionId
      val sut = buildPar01Service(
        parallelTransitions = listOf(
          trp<Par01MainChildFinishRequest.Par01Top>(Stay),
          trp<BackEvent>(DispatchBackTo(topRegion)),
        ),
        onParallelTransition = { receivedEvents.add(it) },
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val topRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par01Top" }
        val bottomRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par01Bottom" }
        val initialBottomActive = initial.regions[bottomRegionId]!!.active

        sut.sendEvent(Event.Back)
        awaitItem() // Back → Finish(Unit) → EnqueueEvent(Par01MainChildFinishRequest.Par01Top)
        awaitItem().apply {
          // child-finish event → parallel Stay
          // L1 fix: active must still be a screen (par01TopIntro), not the flow node (par01Top)
          regions[topRegionId]!!.active.lastSegment().name shouldBe "par01TopIntro"
          regions[bottomRegionId]!!.active shouldBe initialBottomActive
        }
        cancelAndIgnoreRemainingEvents()
      }

      receivedEvents.any { it is Par01MainChildFinishRequest.Par01Top } shouldBe true
    }

    should("EnqueueEvent returned by ParallelFlowNode<Unit> transition is enqueued and dispatched without error") {
      // par01Main (parallel) returns EnqueueEvent(TestEvent("B")) when "A" is received.
      // The drained event "B" propagates through all regions as Ignore — no crash, state stable.
      val sut = buildPar01Service(
        parallelTransitions = listOf(
          TestParallelTransitionSpec(
            eventMatcher = { it is TestEvent && (it as TestEvent).name == "A" },
            transition = EnqueueEvent(TestEvent("B")),
          ),
        ),
      )

      sut.collectTransitions().test {
        awaitItem() // initial

        sut.sendEvent(TestEvent("A"))
        awaitItem() // A processed: par01Main returned EnqueueEvent(B); state unchanged
        val afterB = awaitItem() // B drained: all Ignore; state unchanged

        // Sub-regions are still alive and active — EnqueueEvent from a parallel node is safe
        afterB.regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
          "par01App",
          "par01Top",
          "par01Bottom",
        )
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("navigate within a sub-region does not affect sibling region") {
      val sut = buildPar02Service(
        alphaTransitions = listOf(tr("goToScreen2", Target.par02Alpha.par02AlphaScreen2)),
        betaTransitions = emptyList(),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val alphaRegionId = initial.regions.keys.find { it.path.lastSegment().name == "par02Alpha" }!!
        val betaRegionId = initial.regions.keys.find { it.path.lastSegment().name == "par02Beta" }!!
        val initialBetaActive = initial.regions[betaRegionId]!!.active

        sut.sendEvent(TestEvent("goToScreen2"))

        awaitItem().apply {
          regions[alphaRegionId]!!.active.lastSegment().name shouldBe "par02AlphaScreen2"
          regions[betaRegionId]!!.active shouldBe initialBetaActive
        }
      }
    }

    // Regression — real-world usage. AppFlowNode is a root parallel that decides which sub-region receives
    // Back from its OWN presentation state: if the sheet shows a non-placeholder leaf, route Back to
    // the sheet; otherwise to the head. It expresses that as DispatchBackTo(chosen region) from
    // transition(Event.Back). Every Back press from any app screen flows through this. The way-layer
    // contract asserted here is that DispatchBackTo routes Back to EXACTLY the named region and to no
    // sibling — the app's choice of which region is the consumer's logic.
    should("DispatchBackTo from a parallel routes Back to exactly ONE named sub-region, not its sibling") {
      // The app node decides (from its own state) which sub-region receives Back and returns
      // DispatchBackTo(that region). Back must reach ONLY that region — the orthogonal sibling
      // must NOT see the Back. Here the app picks alpha.
      var alphaRegionId: RegionId? = null
      val backReceivedBy = mutableListOf<String>()
      val sut = buildPar02Service(
        createMainNode = {
          object : ParallelFlowNode<Unit>() {
            override val dismissResult = Unit
            override fun transition(event: Event): FlowTransition<Unit> = if (event == Event.Back) {
              DispatchBackTo(requireNotNull(alphaRegionId) { "capture alphaRegionId before sending Back" })
            } else {
              Ignore
            }
          }
        },
        alphaTransitions = listOf(
          tr("driveAlpha", Target.par02Alpha.par02AlphaScreen2),
          TestFlowTransitionSpec(
            eventMatcher = { ev ->
              if (ev is ru.kode.way.BackEvent) {
                backReceivedBy.add("alpha")
                true
              } else {
                false
              }
            },
            transition = Stay,
          ),
        ),
        betaTransitions = listOf(
          TestFlowTransitionSpec(
            eventMatcher = { ev ->
              if (ev is ru.kode.way.BackEvent) {
                backReceivedBy.add("beta")
                true
              } else {
                false
              }
            },
            transition = Stay,
          ),
        ),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        alphaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par02Alpha" }
        sut.sendEvent(TestEvent("driveAlpha"))
        awaitItem()

        sut.sendEvent(ru.kode.way.Event.Back)
        awaitItem()

        // Back was routed to alpha only. If Back were broadcast to every sub-region (the bug this
        // guards), beta's transition would also have recorded it.
        backReceivedBy shouldBe listOf("alpha")
        cancelAndIgnoreRemainingEvents()
      }
    }

    // Before the fix, NavigationService called invalidateCache once
    // per region with that region's active path; the generated retainAll evicted any cache
    // entry whose key wasn't a prefix of that single path, so siblings in other parallel
    // regions were dropped on every transition. The next access rebuilt their NodeBuilder
    // (and any scope-singleton it held) — observably breaking DI scope contracts in the
    // host application. After the fix, invalidateCache is called once with the union of all
    // regions' alive paths and retains a child if it's a prefix of ANY alive path.
    should("invalidateCache does not evict NodeBuilders that are alive in sibling parallel regions") {
      var alphaCreateCalls = 0
      var betaCreateCalls = 0
      val sut = buildPar02ServiceWithCachedBuilderCounters(
        alphaTransitions = listOf(tr("goToScreen2", Target.par02Alpha.par02AlphaScreen2)),
        onCreateAlpha = { alphaCreateCalls++ },
        onCreateBeta = { betaCreateCalls++ },
      )

      sut.collectTransitions().test {
        awaitItem() // initial: both regions entered → each builder created exactly once
        alphaCreateCalls shouldBe 1
        betaCreateCalls shouldBe 1

        sut.sendEvent(TestEvent("goToScreen2"))
        awaitItem()

        // After the cross-region invalidateCache sweep neither sibling NodeBuilder was
        // re-created. With the per-region call this assertion failed —
        // navigating Alpha invalidated with the Beta region's path and evicted Alpha's
        // (and vice versa for Beta).
        alphaCreateCalls shouldBe 1
        betaCreateCalls shouldBe 1

        cancelAndIgnoreRemainingEvents()
      }
    }

    // L1: back at sub-region flow boundary triggers Finish of the sub-region flow,
    // not a wrong NavigateTo that sets a FlowNode as the active path.
    should("back at sub-region flow boundary triggers sub-region flow finish") {
      val receivedEvents = mutableListOf<Event>()
      val topRegion = Parallel01MainSchema(Parallel01TopSchema(), Parallel01BottomSchema()).par01TopRegionId
      val sut = buildPar01Service(
        parallelTransitions = listOf(
          trp<Par01MainChildFinishRequest.Par01Top>(Stay),
          trp<BackEvent>(DispatchBackTo(topRegion)),
        ),
        onParallelTransition = { event -> receivedEvents.add(event) },
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val topRegionId = initial.regions.keys.find { it.path.lastSegment().name == "par01Top" }!!
        val bottomRegionId = initial.regions.keys.find { it.path.lastSegment().name == "par01Bottom" }!!
        val initialBottomActive = initial.regions[bottomRegionId]!!.active

        sut.sendEvent(Event.Back)

        // Back at par01TopIntro (only screen, direct child of par01Top flow root):
        // With L1 fix → Finish(Unit) → RootFinishRequestEvent → Par01MainChildFinishRequest.Par01Top
        // active path should remain par01TopIntro (screen), not become par01Top (a FlowNode)
        awaitItem() // Back → EnqueueEvent(Par01MainChildFinishRequest.Par01Top)
        awaitItem().apply {
          // child-finish event drained → Stay from parallel
          regions[topRegionId]!!.active.lastSegment().name shouldBe "par01TopIntro"
          // Sibling bottom region is unaffected
          regions[bottomRegionId]!!.active shouldBe initialBottomActive
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
            "par01App",
            "par01Top",
            "par01Bottom",
          )
        }
        cancelAndIgnoreRemainingEvents()
      }

      // The parallel node must have received Par01MainChildFinishRequest.Par01Top
      receivedEvents.any { it is Par01MainChildFinishRequest.Par01Top } shouldBe true
    }

    should("child-finish handler bubbles to parent flow when parallel returns Ignore") {
      // C1+C2: verify both the reactive dispatch path and sibling region preservation.
      // When the parallel returns Ignore for a child-finish event, it bubbles to the parent flow,
      // which handles it with Stay. Assert all sub-regions remain intact with unchanged active paths.
      var topExitCount = 0
      var bottomExitCount = 0
      val topRegion = Parallel01MainSchema(Parallel01TopSchema(), Parallel01BottomSchema()).par01TopRegionId
      val sut = buildPar01Service(
        parallelTransitions = listOf(
          trp<Par01MainChildFinishRequest.Par01Top>(Ignore),
          trp<BackEvent>(DispatchBackTo(topRegion)),
        ),
        topOnExitImpl = { topExitCount++ },
        bottomOnExitImpl = { bottomExitCount++ },
        appTransitions = listOf(
          TestFlowTransitionSpec(
            eventMatcher = { it is Par01MainChildFinishRequest.Par01Top },
            transition = Stay,
          ),
        ),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val topRegionId = initial.regions.keys.find { it.path.lastSegment().name == "par01Top" }!!
        val bottomRegionId = initial.regions.keys.find { it.path.lastSegment().name == "par01Bottom" }!!
        val initialTopActive = initial.regions[topRegionId]!!.active
        val initialBottomActive = initial.regions[bottomRegionId]!!.active

        sut.sendEvent(Event.Back)

        awaitItem() // Back → EnqueueEvent(Par01MainChildFinishRequest.Par01Top)
        awaitItem().apply {
          // child-finish event: parallel Ignore → parent flow Stay
          // Both sub-regions still alive: Stay by parent flow doesn't alter sub-region state
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
            "par01App",
            "par01Top",
            "par01Bottom",
          )
          regions[topRegionId]!!.active shouldBe initialTopActive
          regions[bottomRegionId]!!.active shouldBe initialBottomActive
        }
        cancelAndIgnoreRemainingEvents()
      }

      // Stay from parent flow → no node exits in either sub-region
      topExitCount shouldBe 0
      bottomExitCount shouldBe 0
    }

    should("start called twice throws") {
      val sut = buildPar01Service()
      sut.start()
      io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
        sut.start()
      }
    }

    should("Finish from parallel node triggers parent flow finish") {
      var onFinishRequestInvoked = false
      var alphaExitCount = 0
      var betaExitCount = 0
      val appSchema = Parallel02Schema(
        par02MainSchema = Parallel02MainSchema(
          par02AlphaSchema = Parallel02AlphaSchema(),
          par02BetaSchema = Parallel02BetaSchema(),
        ),
      )
      val alphaNodeBuilder = Par02AlphaNodeBuilder(
        nodeFactory = object : Par02AlphaNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> =
            TestFlowNode(initialTarget = Target.par02Alpha.par02AlphaScreen1, onExitImpl = { alphaExitCount++ })
          override fun createPar02AlphaScreen1Node(): ScreenNode = TestScreenNode()
          override fun createPar02AlphaScreen2Node(): ScreenNode = TestScreenNode()
        },
        schema = Parallel02AlphaSchema(),
      )
      val betaNodeBuilder = Par02BetaNodeBuilder(
        nodeFactory = object : Par02BetaNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> =
            TestFlowNode(initialTarget = Target.par02Beta.par02BetaScreen1, onExitImpl = { betaExitCount++ })
          override fun createPar02BetaScreen1Node(): ScreenNode = TestScreenNode()
        },
        schema = Parallel02BetaSchema(),
      )
      val mainNodeBuilder = Par02MainNodeBuilder(
        nodeFactory = object : Par02MainNodeBuilder.Factory {
          override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
            parallelTransitions = listOf(
              TestParallelTransitionSpec(
                eventMatcher = { it is TestEvent && (it as TestEvent).name == "exit" },
                transition = Finish(Unit),
              ),
            ),
          )
          override fun createPar02AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
          override fun createPar02BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
        },
        Parallel02MainSchema(Parallel02AlphaSchema(), Parallel02BetaSchema()),
      )
      val appNodeBuilder = Par02AppNodeBuilder(
        nodeFactory = object : Par02AppNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par02App.par02Main,
          )
          override fun createPar02MainNodeBuilder(): NodeBuilder = mainNodeBuilder
        },
        schema = appSchema,
      )
      val sut = NavigationService<Unit>(
        nodeBuilder = appNodeBuilder,
        onFinishRequest = {
          onFinishRequestInvoked = true
          Ignore
        },
      )

      sut.collectTransitions().test {
        awaitItem()
        sut.sendEvent(TestEvent("exit"))
        cancelAndIgnoreRemainingEvents()
      }

      onFinishRequestInvoked shouldBe true
      // onFinishRequest returned Ignore → parallel stays alive, no teardown, sub-region onExit does NOT fire
      alphaExitCount shouldBe 0
      betaExitCount shouldBe 0
    }

    // L2: AbsoluteTarget to a FlowNode path follows the flow's initial chain to reach a screen.
    should("AbsoluteTarget to flow node follows initial chain") {
      var alphaFlowRootPath: Path? = null

      val sut = buildPar02ServiceWithCustomApp(
        createAppNode = {
          object : FlowNode<Unit> {
            override val dismissResult = Unit
            override val initial: Target = Target.par02App.par02Main
            override fun transition(event: Event): FlowTransition<Unit> = when {
              event is TestEvent && event.name == "deeplink" ->
                alphaFlowRootPath?.let { NavigateTo(AbsoluteTarget(it)) } ?: Ignore

              else -> Ignore
            }
          }
        },
        alphaTransitions = listOf(tr("screen2", Target.par02Alpha.par02AlphaScreen2)),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val alphaRegionId = initial.regions.keys.find { it.path.lastSegment().name == "par02Alpha" }!!
        val betaRegionId = initial.regions.keys.find { it.path.lastSegment().name == "par02Beta" }!!
        alphaFlowRootPath = alphaRegionId.path
        val initialBetaActive = initial.regions[betaRegionId]!!.active

        // Navigate alpha to screen2 so it is no longer at the initial screen
        sut.sendEvent(TestEvent("screen2"))
        awaitItem().apply {
          regions[alphaRegionId]!!.active.lastSegment().name shouldBe "par02AlphaScreen2"
        }

        // Navigate via AbsoluteTarget to the alpha flow root (a FlowNode)
        sut.sendEvent(TestEvent("deeplink"))
        // With L2 fix: follows initial chain → par02AlphaScreen1
        awaitItem().apply {
          regions[alphaRegionId]!!.active.lastSegment().name shouldBe "par02AlphaScreen1"
          // Sibling beta region must be unaffected by the AbsoluteTarget deeplink into alpha
          regions[betaRegionId]!!.active shouldBe initialBetaActive
        }

        cancelAndIgnoreRemainingEvents()
      }
    }

    should("deepestRegion selects the region with the deepest active path") {
      val alphaId = RegionId(Path("alpha"))
      val betaId = RegionId(Path("beta"))
      deepestRegion(
        mapOf(
          alphaId to Path("alpha", "flow", "screenA"), // length 3
          betaId to Path("beta", "screenB"), // length 2
        ),
      ) shouldBe alphaId
    }

    should("deepestRegion uses region path as tiebreaker when depths equal") {
      val alphaId = RegionId(Path("alpha"))
      val gammaId = RegionId(Path("gamma"))
      // maxByOrNull ascending → picks highest key.path.toString() = "gamma"
      deepestRegion(
        linkedMapOf(
          alphaId to Path("alpha", "screenA"), // length 2
          gammaId to Path("gamma", "screenC"), // length 2
        ),
      ) shouldBe gammaId
    }

    should("onEntry and onExit are called for the parallel node itself on enter and exit") {
      var mainEntryCount = 0
      var mainExitCount = 0
      val sut = buildPar03Service(
        onMainEntry = { mainEntryCount++ },
        onMainExit = { mainExitCount++ },
        appTransitions = listOf(tr("goToPage", Target.par03App.par03Page)),
      )

      sut.collectTransitions().test {
        awaitItem()
        mainEntryCount shouldBe 1
        mainExitCount shouldBe 0

        sut.sendEvent(TestEvent("goToPage"))
        awaitItem()
        mainEntryCount shouldBe 1
        mainExitCount shouldBe 1
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("stale sub-regions are pruned when parallel node leaves alive set") {
      var alphaExitCount = 0
      var betaExitCount = 0
      val sut = buildPar03Service(
        appTransitions = listOf(tr("goToPage", Target.par03App.par03Page)),
        onAlphaExit = { alphaExitCount++ },
        onBetaExit = { betaExitCount++ },
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        initial.regions.keys.map { it.path.lastSegment().name }
          .shouldContainOnly("par03App", "par03Alpha", "par03Beta")

        sut.sendEvent(TestEvent("goToPage"))

        awaitItem().apply {
          regions.keys.size shouldBe 1
          regions.keys.first().path.lastSegment().name shouldBe "par03App"
          regions.values.first().active.lastSegment().name shouldBe "par03Page"
        }
        cancelAndIgnoreRemainingEvents()
      }

      // Both sub-region root flows must have received onExit when their regions were pruned
      alphaExitCount shouldBe 1
      betaExitCount shouldBe 1
    }

    should("re-entering parallel after leaving creates fresh sub-regions with onEntry") {
      var alphaEntryCount = 0
      var betaEntryCount = 0
      val sut = buildPar03Service(
        onAlphaEntry = { alphaEntryCount++ },
        onBetaEntry = { betaEntryCount++ },
        appTransitions = listOf(
          tr("goToPage", Target.par03App.par03Page),
          tr("goToMain", Target.par03App.par03Main),
        ),
      )

      sut.collectTransitions().test {
        awaitItem()
        alphaEntryCount shouldBe 1
        betaEntryCount shouldBe 1

        sut.sendEvent(TestEvent("goToPage"))
        awaitItem()

        sut.sendEvent(TestEvent("goToMain"))
        awaitItem()
        alphaEntryCount shouldBe 2
        betaEntryCount shouldBe 2
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("Stay from ParallelFlowNode<Unit>.transition keeps active state unchanged") {
      val sut = buildPar01Service(
        parallelTransitions = listOf(trp<TestEvent>(Stay)),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val topRegionId = initial.regions.keys.find { it.path.lastSegment().name == "par01Top" }!!
        val bottomRegionId = initial.regions.keys.find { it.path.lastSegment().name == "par01Bottom" }!!
        val topActive = initial.regions[topRegionId]!!.active
        val bottomActive = initial.regions[bottomRegionId]!!.active

        sut.sendEvent(TestEvent("anything"))

        awaitItem().apply {
          regions[topRegionId]!!.active shouldBe topActive
          regions[bottomRegionId]!!.active shouldBe bottomActive
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    // "Finish with typed non-Unit result from parallel node is received by
    // onFinishRequest". Under the new ParallelFlowNode<R> typing, a parallel-flow's Finish<R>
    // is a compile-time-typed value — the previous test verified runtime behaviour that's now
    // impossible to express incorrectly. The same coverage lives in flow-finish tests.

    should("Back dispatches through inner parallel's strategy in nested parallel topology") {
      // Proves the chosenActiveNode is ParallelFlowNode<*> fix in dispatchBackThroughParallel:
      // when the outer parallel's strategy selects a sub-region whose active is itself a ParallelFlowNode<Unit>,
      // Back must recurse into that inner parallel's sub-regions rather than navigating up to the parent.
      // Also proves the direct-children filterKeys fix: outer strategy receives only 2 sub-regions
      // (par04Alpha, par04Beta), not 4 (which would include par04InnerA and par04InnerB).
      val sut = buildPar04Service()

      sut.collectTransitions().test {
        val initial = awaitItem()

        // All 5 regions are alive: app, outerAlpha, outerBeta, innerA, innerB
        initial.regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
          "par04App",
          "par04Alpha",
          "par04Beta",
          "par04InnerA",
          "par04InnerB",
        )
        val innerARegionId = initial.regions.keys.first { it.path.lastSegment().name == "par04InnerA" }
        // innerA starts at screen2 (the initial target)
        initial.regions[innerARegionId]!!.active.lastSegment().name shouldBe "par04InnerAScreen2"

        sut.sendEvent(Event.Back)

        // Back should navigate within innerA from screen2 → screen1
        // (not finish the alpha sub-region, which is what happened before the ParallelFlowNode<Unit> active fix)
        awaitItem().apply {
          regions[innerARegionId]!!.active.lastSegment().name shouldBe "par04InnerAScreen1"
          // All 5 regions remain alive — alpha was NOT finished
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
            "par04App",
            "par04Alpha",
            "par04Beta",
            "par04InnerA",
            "par04InnerB",
          )
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("same event dispatched to two active sub-regions is handled independently by each") {
      // Verifies resolveTransition's fold accumulates non-Ignore transitions from ALL sub-regions.
      // Alpha navigates to screen2; Beta handles with Stay. Both contribute to targetPaths.
      val alphaSchema = Parallel02AlphaSchema()
      val betaSchema = Parallel02BetaSchema()
      val alphaNodeBuilder = Par02AlphaNodeBuilder(
        nodeFactory = object : Par02AlphaNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par02Alpha.par02AlphaScreen1,
            transitions = listOf(tr("nav", Target.par02Alpha.par02AlphaScreen2)),
          )
          override fun createPar02AlphaScreen1Node(): ScreenNode = TestScreenNode()
          override fun createPar02AlphaScreen2Node(): ScreenNode = TestScreenNode()
        },
        schema = alphaSchema,
      )
      val betaNodeBuilder = Par02BetaNodeBuilder(
        nodeFactory = object : Par02BetaNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par02Beta.par02BetaScreen1,
            transitions = listOf(tr("nav", Stay)),
          )
          override fun createPar02BetaScreen1Node(): ScreenNode = TestScreenNode()
        },
        schema = betaSchema,
      )
      val mainNodeBuilder = Par02MainNodeBuilder(
        nodeFactory = object : Par02MainNodeBuilder.Factory {
          override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
          override fun createPar02AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
          override fun createPar02BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
        },
        Parallel02MainSchema(alphaSchema, betaSchema),
      )
      val appNodeBuilder = Par02AppNodeBuilder(
        nodeFactory = object : Par02AppNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par02App.par02Main,
          )
          override fun createPar02MainNodeBuilder(): NodeBuilder = mainNodeBuilder
        },
        schema = Parallel02Schema(Parallel02MainSchema(alphaSchema, betaSchema)),
      )
      val sut = NavigationService<Unit>(
        nodeBuilder = appNodeBuilder,
        onFinishRequest = { Stay },
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val alphaRegionId = initial.regions.keys.first { "par02Alpha" in it.path.toString() }
        val betaRegionId = initial.regions.keys.first { "par02Beta" in it.path.toString() }

        sut.sendEvent(TestEvent("nav"))
        val after = awaitItem()

        // Alpha sub-region navigated independently
        after.regions[alphaRegionId]!!.active.lastSegment().name shouldBe "par02AlphaScreen2"
        // Beta sub-region handled with Stay — unchanged
        after.regions[betaRegionId]!!.active.lastSegment().name shouldBe "par02BetaScreen1"
        // Both sub-regions remain alive
        (after.regions.containsKey(alphaRegionId)) shouldBe true
        (after.regions.containsKey(betaRegionId)) shouldBe true
        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── AbsoluteTarget into not-yet-created parallel sub-region ──────────────────────────────────

    should(
      "AbsoluteTarget into not-yet-created parallel sub-region initializes sub-regions and routes to specific screen",
    ) {
      // par03 has par03Page (non-parallel) and par03Main (parallel → alpha, beta).
      // Test: navigate alpha to screen2, leave to par03Page (pruning sub-regions), then
      // deeplink back to alpha screen2 via AbsoluteTarget — sub-regions must be recreated.
      var alphaScreen2AbsPath: Path? = null
      val sut = buildPar03ServiceWithCustomApp(
        createAppNode = {
          object : FlowNode<Unit> {
            override val initial: Target = Target.par03App.par03Main
            override val dismissResult = Unit
            override fun transition(event: Event): FlowTransition<Unit> = when {
              event is TestEvent && event.name == "goToPage" -> NavigateTo(Target.par03App.par03Page)

              event is TestEvent && event.name == "deeplink" ->
                alphaScreen2AbsPath?.let { NavigateTo(AbsoluteTarget(it)) } ?: Ignore

              else -> Ignore
            }
          }
        },
        alphaTransitions = listOf(tr("goToScreen2", Target.par03Alpha.par03AlphaScreen2)),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val alphaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par03Alpha" }
        val betaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par03Beta" }

        // Navigate alpha to screen2, then capture the absolute path
        sut.sendEvent(TestEvent("goToScreen2"))
        awaitItem().apply {
          alphaScreen2AbsPath = regions[alphaRegionId]!!.active
          alphaScreen2AbsPath!!.lastSegment().name shouldBe "par03AlphaScreen2"
        }

        // Leave the parallel — both alpha and beta sub-regions are pruned
        sut.sendEvent(TestEvent("goToPage"))
        awaitItem().apply {
          regions.keys.size shouldBe 1
          regions.keys.first().path.lastSegment().name shouldBe "par03App"
          regions.values.first().active.lastSegment().name shouldBe "par03Page"
        }

        // AbsoluteTarget deeplink back into alpha screen2 (sub-regions don't exist yet)
        sut.sendEvent(TestEvent("deeplink"))
        awaitItem().apply {
          // Sub-regions must be recreated
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly("par03App", "par03Alpha", "par03Beta")
          // Alpha is at the specific deep-linked screen, not its initial screen
          regions[alphaRegionId]!!.active.lastSegment().name shouldBe "par03AlphaScreen2"
          // Beta initializes to its default initial screen
          regions[betaRegionId]!!.active.lastSegment().name shouldBe "par03BetaScreen"
          // App region stops at the parallel node boundary
          val appRegion = regions.entries.find { it.key.path.lastSegment().name == "par03App" }!!.value
          appRegion.active.lastSegment().name shouldBe "par03Main"
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── par05: LocalParallel with local (non-imported) flow children ─────────────────────────────

    should("LocalParallel with local flow children initializes sub-regions and resolves initial state") {
      val sut = buildPar05Service()

      sut.collectTransitions().test {
        awaitItem().apply {
          // All three regions created: app root, par05Alpha, par05Beta
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
            "par05App",
            "par05Alpha",
            "par05Beta",
          )
          val alphaRegion = regions.entries.find { it.key.path.lastSegment().name == "par05Alpha" }!!.value
          alphaRegion.active.lastSegment().name shouldBe "par05AlphaScreen1"
          val betaRegion = regions.entries.find { it.key.path.lastSegment().name == "par05Beta" }!!.value
          betaRegion.active.lastSegment().name shouldBe "par05BetaScreen"
        }
      }
    }

    should("LocalParallel with local flow children navigates within sub-region without disturbing sibling") {
      val sut = buildPar05Service(
        alphaTransitions = listOf(tr("goToScreen2", Target.par05Alpha.par05AlphaScreen2)),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val alphaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par05Alpha" }
        val betaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par05Beta" }
        val initialBetaActive = initial.regions[betaRegionId]!!.active

        sut.sendEvent(TestEvent("goToScreen2"))
        awaitItem().apply {
          regions[alphaRegionId]!!.active.lastSegment().name shouldBe "par05AlphaScreen2"
          regions[betaRegionId]!!.active shouldBe initialBetaActive
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("LocalParallel with local flow children dispatches child-finish events on back") {
      val receivedEvents = mutableListOf<Event>()
      val sut = buildPar05Service(
        parallelTransitions = listOf(
          trp<Par05MainChildFinishRequest.Par05Alpha>(Stay),
          trp<BackEvent>(DispatchBackTo(Parallel05MainSchema().par05AlphaRegionId)),
        ),
        onParallelTransition = { receivedEvents.add(it) },
      )

      sut.collectTransitions().test {
        awaitItem()
        sut.sendEvent(Event.Back)
        awaitItem() // Back at par05AlphaScreen1 → Finish → EnqueueEvent(Par05Alpha)
        awaitItem() // child-finish drained → parallel Stay
        cancelAndIgnoreRemainingEvents()
      }

      receivedEvents.any { it is Par05MainChildFinishRequest.Par05Alpha } shouldBe true
    }

    // "parallel Finish in response to child-finish event delivers typed
    // result to onFinishRequest". With ParallelFlowNode<R>, the result type is compile-time
    // checked against the schema's resultType (Unit by default). The "typed result delivered
    // to onFinishRequest" path is exercised by the regular flow finish tests.

    // ── parfm: flat-mixed parallel (imported alpha + local beta) ──────────────────────────────────

    should("flat-mixed parallel initializes sub-regions with imported alpha and local beta") {
      val sut = buildParfmService()

      sut.collectTransitions().test {
        awaitItem().apply {
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
            "parfmApp",
            "parfmAlpha",
            "parfmBeta",
          )
          val alphaRegion = regions.entries.find { it.key.path.lastSegment().name == "parfmAlpha" }!!.value
          alphaRegion.active.lastSegment().name shouldBe "parfmAlphaScreen1"
          val betaRegion = regions.entries.find { it.key.path.lastSegment().name == "parfmBeta" }!!.value
          betaRegion.active.lastSegment().name shouldBe "parfmBetaScreen"
        }
      }
    }

    should("flat-mixed parallel navigate within imported sub-region does not affect local sub-region") {
      val sut = buildParfmService(
        alphaTransitions = listOf(tr("goToScreen2", Target.parfmAlpha.parfmAlphaScreen2)),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val alphaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "parfmAlpha" }
        val betaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "parfmBeta" }
        val initialBetaActive = initial.regions[betaRegionId]!!.active

        sut.sendEvent(TestEvent("goToScreen2"))
        awaitItem().apply {
          regions[alphaRegionId]!!.active.lastSegment().name shouldBe "parfmAlphaScreen2"
          regions[betaRegionId]!!.active shouldBe initialBetaActive
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("flat-mixed parallel back at local beta sub-region flow boundary triggers child-finish") {
      val receivedEvents = mutableListOf<Event>()
      val sut = buildParfmService(
        parallelTransitions = listOf(
          trp<ParfmMainChildFinishRequest.ParfmBeta>(Stay),
          trp<BackEvent>(
            DispatchBackTo(
              ParallelFlatMixMainSchema(parfmAlphaSchema = ParallelFlatMixAlphaSchema()).parfmBetaRegionId,
            ),
          ),
        ),
        onParallelTransition = { receivedEvents.add(it) },
      )

      sut.collectTransitions().test {
        awaitItem()
        sut.sendEvent(Event.Back)
        awaitItem() // Back at parfmBetaScreen → Finish → EnqueueEvent(ParfmBeta)
        awaitItem() // child-finish drained → parallel Stay
        cancelAndIgnoreRemainingEvents()
      }

      receivedEvents.any { it is ParfmMainChildFinishRequest.ParfmBeta } shouldBe true
    }

    should("flat-mixed parallel back at imported alpha sub-region flow boundary triggers child-finish") {
      val receivedEvents = mutableListOf<Event>()
      val sut = buildParfmService(
        parallelTransitions = listOf(
          trp<ParfmMainChildFinishRequest.ParfmAlpha>(Stay),
          trp<BackEvent>(
            DispatchBackTo(
              ParallelFlatMixMainSchema(parfmAlphaSchema = ParallelFlatMixAlphaSchema()).parfmAlphaRegionId,
            ),
          ),
        ),
        onParallelTransition = { receivedEvents.add(it) },
      )

      sut.collectTransitions().test {
        awaitItem()
        sut.sendEvent(Event.Back)
        awaitItem() // Back at parfmAlphaScreen1 → Finish → EnqueueEvent(ParfmAlpha)
        awaitItem() // child-finish drained → parallel Stay
        cancelAndIgnoreRemainingEvents()
      }

      receivedEvents.any { it is ParfmMainChildFinishRequest.ParfmAlpha } shouldBe true
    }

    // ── par06: all-local depth-2 nested parallel ──────────────────────────────────────────────────

    should("all-local depth-2 parallel initializes all sub-regions including inner parallel") {
      val sut = buildPar06Service()

      sut.collectTransitions().test {
        awaitItem().apply {
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
            "par06App",
            "par06Alpha",
            "par06Beta",
            "par06InnerA",
            "par06InnerB",
          )
          val innerARegion = regions.entries.find { it.key.path.lastSegment().name == "par06InnerA" }!!.value
          innerARegion.active.lastSegment().name shouldBe "par06InnerAScreen1"
          val innerBRegion = regions.entries.find { it.key.path.lastSegment().name == "par06InnerB" }!!.value
          innerBRegion.active.lastSegment().name shouldBe "par06InnerBScreen"
          val betaRegion = regions.entries.find { it.key.path.lastSegment().name == "par06Beta" }!!.value
          betaRegion.active.lastSegment().name shouldBe "par06BetaScreen"
        }
      }
    }

    should("all-local depth-2 parallel back dispatches through inner parallel strategy") {
      val sut = buildPar06Service(
        innerAInitial = Target.par06InnerA.par06InnerAScreen2,
        outerParallelTransitions = listOf(trp<BackEvent>(DispatchBackTo(Parallel06MainSchema().par06AlphaRegionId))),
        innerAlphaParallelTransitions = listOf(trp<BackEvent>(DispatchBackTo(Par06AlphaSchema().par06InnerARegionId))),
      )

      sut.collectTransitions().test {
        awaitItem().apply {
          val innerARegion = regions.entries.find { it.key.path.lastSegment().name == "par06InnerA" }!!.value
          innerARegion.active.lastSegment().name shouldBe "par06InnerAScreen2"
        }
        sut.sendEvent(Event.Back)
        awaitItem().apply {
          val innerARegion = regions.entries.find { it.key.path.lastSegment().name == "par06InnerA" }!!.value
          innerARegion.active.lastSegment().name shouldBe "par06InnerAScreen1"
          // All 5 regions still alive
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
            "par06App",
            "par06Alpha",
            "par06Beta",
            "par06InnerA",
            "par06InnerB",
          )
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("all-local depth-2 parallel navigate within inner sub-region does not affect outer beta") {
      val sut = buildPar06Service(
        innerATransitions = listOf(tr("goToScreen2", Target.par06InnerA.par06InnerAScreen2)),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val innerARegionId = initial.regions.keys.first { it.path.lastSegment().name == "par06InnerA" }
        val betaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par06Beta" }
        val initialBetaActive = initial.regions[betaRegionId]!!.active

        sut.sendEvent(TestEvent("goToScreen2"))
        awaitItem().apply {
          regions[innerARegionId]!!.active.lastSegment().name shouldBe "par06InnerAScreen2"
          regions[betaRegionId]!!.active shouldBe initialBetaActive
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── par06m: depth-2 mixed parallel (imported inner parallel + local beta) ─────────────────────

    should("mixed depth-2 parallel initializes all sub-regions with imported inner parallel and local beta") {
      val sut = buildPar06mService()

      sut.collectTransitions().test {
        awaitItem().apply {
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
            "par06mApp",
            "par06mAlpha",
            "par06mBeta",
            "par06mInnerA",
            "par06mInnerB",
          )
          val innerARegion = regions.entries.find { it.key.path.lastSegment().name == "par06mInnerA" }!!.value
          innerARegion.active.lastSegment().name shouldBe "par06mInnerAScreen"
          val innerBRegion = regions.entries.find { it.key.path.lastSegment().name == "par06mInnerB" }!!.value
          innerBRegion.active.lastSegment().name shouldBe "par06mInnerBScreen"
          val betaRegion = regions.entries.find { it.key.path.lastSegment().name == "par06mBeta" }!!.value
          betaRegion.active.lastSegment().name shouldBe "par06mBetaScreen"
        }
      }
    }

    should("mixed depth-2 parallel back dispatches through imported inner parallel strategy") {
      val receivedEvents = mutableListOf<Event>()
      val sut = buildPar06mService(
        outerParallelTransitions = listOf(
          trp<BackEvent>(
            DispatchBackTo(
              Parallel06MixedMainSchema(par06mAlphaSchema = Parallel06MixedAlphaSchema()).par06mAlphaRegionId,
            ),
          ),
        ),
        alphaInnerTransitions = listOf(
          trp<Par06mAlphaChildFinishRequest.Par06mInnerA>(Stay),
          trp<BackEvent>(DispatchBackTo(Parallel06MixedAlphaSchema().par06mInnerARegionId)),
        ),
        alphaInnerTransitionCallback = { receivedEvents.add(it) },
      )

      sut.collectTransitions().test {
        awaitItem()
        sut.sendEvent(Event.Back)
        awaitItem() // Back at par06mInnerAScreen → Finish → EnqueueEvent(Par06mInnerA)
        awaitItem() // child-finish drained → alpha parallel Stay
        cancelAndIgnoreRemainingEvents()
      }

      receivedEvents.any { it is Par06mAlphaChildFinishRequest.Par06mInnerA } shouldBe true
    }

    should("mixed depth-2 parallel local beta unaffected by inner alpha navigation") {
      val receivedEvents = mutableListOf<Event>()
      val sut = buildPar06mService(
        outerParallelTransitions = listOf(
          trp<Par06mMainChildFinishRequest.Par06mBeta>(Stay),
          trp<BackEvent>(
            DispatchBackTo(
              Parallel06MixedMainSchema(par06mAlphaSchema = Parallel06MixedAlphaSchema()).par06mBetaRegionId,
            ),
          ),
        ),
        outerParallelTransitionCallback = { receivedEvents.add(it) },
      )

      sut.collectTransitions().test {
        awaitItem()
        sut.sendEvent(Event.Back)
        awaitItem() // Back at par06mBetaScreen → Finish → EnqueueEvent(Par06mBeta)
        awaitItem() // child-finish drained → outer parallel Stay
        cancelAndIgnoreRemainingEvents()
      }

      receivedEvents.any { it is Par06mMainChildFinishRequest.Par06mBeta } shouldBe true
    }

    // Behavioral improvement: after the dispatchBackThroughParallel refactor the full end-to-root
    // chain is walked in the chosen sub-region, matching how flow Back works.
    // A FlowNode inside a parallel sub-region that returns non-Ignore on Back now has its result
    // used; previously only the deepest screen was asked and intermediate nodes were skipped.
    should("FlowNode inside parallel sub-region has transition(Back) called") {
      var flowNodeBackCallCount = 0
      val appSchema = Parallel01Schema(
        par01MainSchema = Parallel01MainSchema(
          par01TopSchema = Parallel01TopSchema(),
          par01BottomSchema = Parallel01BottomSchema(),
        ),
      )
      val topNodeBuilder = Par01TopNodeBuilder(
        nodeFactory = object : Par01TopNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = object : FlowNode<Unit> {
            override val initial: Target = Target.par01Top.par01TopIntro
            override val dismissResult = Unit
            override fun transition(event: Event): FlowTransition<Unit> {
              if (event == Event.Back) flowNodeBackCallCount++
              return if (event == Event.Back) Stay else Ignore
            }
          }
          override fun createPar01TopIntroNode(): ScreenNode = TestScreenNode()
        },
        schema = Parallel01TopSchema(),
      )
      val bottomNodeBuilder = Par01BottomNodeBuilder(
        nodeFactory = object : Par01BottomNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par01Bottom.par01BottomMain)
          override fun createPar01BottomMainNode(): ScreenNode = TestScreenNode()
        },
        schema = Parallel01BottomSchema(),
      )
      val mainNodeBuilder = Par01MainNodeBuilder(
        nodeFactory = object : Par01MainNodeBuilder.Factory {
          override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
            parallelTransitions = listOf(
              trp<BackEvent>(
                DispatchBackTo(Parallel01MainSchema(Parallel01TopSchema(), Parallel01BottomSchema()).par01TopRegionId),
              ),
            ),
          )
          override fun createPar01BottomNodeBuilder(): NodeBuilder = bottomNodeBuilder
          override fun createPar01TopNodeBuilder(): NodeBuilder = topNodeBuilder
        },
        Parallel01MainSchema(Parallel01TopSchema(), Parallel01BottomSchema()),
      )
      val appNodeBuilder = Par01AppNodeBuilder(
        nodeFactory = object : Par01AppNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par01App.par01Main)
          override fun createPar01MainNodeBuilder(): NodeBuilder = mainNodeBuilder
        },
        schema = appSchema,
      )
      val sut = NavigationService<Unit>(
        nodeBuilder = appNodeBuilder,
        onFinishRequest = { Ignore },
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val topRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par01Top" }
        val bottomRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par01Bottom" }

        sut.sendEvent(Event.Back)

        // par01TopIntro screen returns Ignore → chain bubbles to flow root → flow root returns Stay
        awaitItem().apply {
          regions[topRegionId]!!.active.lastSegment().name shouldBe "par01TopIntro"
          regions[bottomRegionId]!!.active.lastSegment().name shouldBe "par01BottomMain"
        }
        cancelAndIgnoreRemainingEvents()
      }

      // The FlowNode's transition(Back) was invoked — the full end-to-root chain was walked
      flowNodeBackCallCount shouldBe 1
    }

    should("NavigateTo with multiple AbsoluteTargets into different sub-regions of a cold parallel") {
      // core-3: when a single NavigateTo carries multiple AbsoluteTargets and the parallel is cold
      // (its sub-regions don't exist yet), each target must contribute to the SAME parallel
      // initialization without sibling targets being overwritten with defaults.
      // Setup: navigate alpha to screen2 and beta to screen, capture both absolute paths.
      // Then leave the parallel (pruning all sub-regions). Finally send a single NavigateTo with
      // AbsoluteTarget(alpha.screen2) AND AbsoluteTarget(beta.screen). Both must land where asked.
      var alphaScreen2Abs: Path? = null
      var betaScreenAbs: Path? = null
      val sut = buildPar03ServiceWithCustomApp(
        createAppNode = {
          object : FlowNode<Unit> {
            override val initial: Target = Target.par03App.par03Main
            override val dismissResult = Unit
            override fun transition(event: Event): FlowTransition<Unit> = when {
              event is TestEvent && event.name == "goToPage" -> NavigateTo(Target.par03App.par03Page)

              event is TestEvent && event.name == "deeplinkBoth" -> {
                val alpha = alphaScreen2Abs
                val beta = betaScreenAbs
                if (alpha != null && beta != null) {
                  NavigateTo(listOf(AbsoluteTarget(alpha), AbsoluteTarget(beta)))
                } else {
                  Ignore
                }
              }

              else -> Ignore
            }
          }
        },
        alphaTransitions = listOf(tr("goToScreen2", Target.par03Alpha.par03AlphaScreen2)),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val alphaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par03Alpha" }
        val betaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par03Beta" }
        // Capture the initial beta active absolute path — beta only has one screen.
        betaScreenAbs = initial.regions[betaRegionId]!!.active
        betaScreenAbs!!.lastSegment().name shouldBe "par03BetaScreen"

        sut.sendEvent(TestEvent("goToScreen2"))
        awaitItem().apply {
          alphaScreen2Abs = regions[alphaRegionId]!!.active
          alphaScreen2Abs!!.lastSegment().name shouldBe "par03AlphaScreen2"
        }

        // Leave the parallel — both alpha and beta sub-regions are pruned.
        sut.sendEvent(TestEvent("goToPage"))
        awaitItem().apply {
          regions.keys.size shouldBe 1
          regions.keys.first().path.lastSegment().name shouldBe "par03App"
        }

        // Single NavigateTo with two AbsoluteTargets into different sub-regions of the cold parallel.
        sut.sendEvent(TestEvent("deeplinkBoth"))
        awaitItem().apply {
          // Sub-regions recreated.
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
            "par03App",
            "par03Alpha",
            "par03Beta",
          )
          // Both deeplink targets landed where requested — neither was reset to its default initial
          // by a sibling AbsoluteTarget reinitializing the same parallel.
          regions[alphaRegionId]!!.active.lastSegment().name shouldBe "par03AlphaScreen2"
          regions[betaRegionId]!!.active.lastSegment().name shouldBe "par03BetaScreen"
          // App region stops at the parallel node boundary.
          val appRegion = regions.entries.find { it.key.path.lastSegment().name == "par03App" }!!.value
          appRegion.active.lastSegment().name shouldBe "par03Main"
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("NavigateTo with multiple AbsoluteTargets into different sub-regions is order-invariant across regions") {
      // Companion to the cold-parallel sibling test above: targets that resolve to DIFFERENT regions
      // are independent, so reversing their order in the list yields the same final state. This
      // deeplink lists beta BEFORE alpha (the reverse of the sibling test); both must still land.
      var alphaScreen2Abs: Path? = null
      var betaScreenAbs: Path? = null
      val sut = buildPar03ServiceWithCustomApp(
        createAppNode = {
          object : FlowNode<Unit> {
            override val initial: Target = Target.par03App.par03Main
            override val dismissResult = Unit
            override fun transition(event: Event): FlowTransition<Unit> = when {
              event is TestEvent && event.name == "goToPage" -> NavigateTo(Target.par03App.par03Page)

              event is TestEvent && event.name == "deeplinkReversed" -> {
                val alpha = alphaScreen2Abs
                val beta = betaScreenAbs
                if (alpha != null && beta != null) {
                  // beta FIRST, alpha SECOND — reverse of the sibling test's target order.
                  NavigateTo(listOf(AbsoluteTarget(beta), AbsoluteTarget(alpha)))
                } else {
                  Ignore
                }
              }

              else -> Ignore
            }
          }
        },
        alphaTransitions = listOf(tr("goToScreen2", Target.par03Alpha.par03AlphaScreen2)),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val alphaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par03Alpha" }
        val betaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par03Beta" }
        betaScreenAbs = initial.regions[betaRegionId]!!.active

        sut.sendEvent(TestEvent("goToScreen2"))
        awaitItem().apply { alphaScreen2Abs = regions[alphaRegionId]!!.active }

        // Leave the parallel — both sub-regions pruned (cold).
        sut.sendEvent(TestEvent("goToPage"))
        awaitItem().apply { regions.keys.size shouldBe 1 }

        // Reverse-order deeplink into the cold parallel: same landing as the forward-order test.
        sut.sendEvent(TestEvent("deeplinkReversed"))
        awaitItem().apply {
          regions[alphaRegionId]!!.active.lastSegment().name shouldBe "par03AlphaScreen2"
          regions[betaRegionId]!!.active.lastSegment().name shouldBe "par03BetaScreen"
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("NavigateTo with multiple AbsoluteTargets into an already-active (warm) parallel routes each region") {
      // Warm complement to the cold-parallel sibling test: the parallel is already active (alpha at
      // Screen2, beta at its screen) and a single NavigateTo carrying two AbsoluteTargets re-routes
      // both live regions at once, without rebuilding the parallel or resetting untargeted structure.
      var alphaScreenAbs: Path? = null
      var betaScreenAbs: Path? = null
      val sut = buildPar03ServiceWithCustomApp(
        createAppNode = {
          object : FlowNode<Unit> {
            override val initial: Target = Target.par03App.par03Main
            override val dismissResult = Unit
            override fun transition(event: Event): FlowTransition<Unit> = when {
              event is TestEvent && event.name == "deeplinkBothWarm" -> {
                val alpha = alphaScreenAbs
                val beta = betaScreenAbs
                if (alpha != null && beta != null) {
                  NavigateTo(listOf(AbsoluteTarget(alpha), AbsoluteTarget(beta)))
                } else {
                  Ignore
                }
              }

              else -> Ignore
            }
          }
        },
        alphaTransitions = listOf(tr("goToScreen2", Target.par03Alpha.par03AlphaScreen2)),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val alphaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par03Alpha" }
        val betaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "par03Beta" }
        // Capture alpha's DEFAULT screen and beta's screen (both warm/active from start).
        alphaScreenAbs = initial.regions[alphaRegionId]!!.active
        alphaScreenAbs!!.lastSegment().name shouldBe "par03AlphaScreen"
        betaScreenAbs = initial.regions[betaRegionId]!!.active

        // Move alpha to Screen2 so the deeplink back to Screen is a real change; parallel stays warm.
        sut.sendEvent(TestEvent("goToScreen2"))
        awaitItem().regions[alphaRegionId]!!.active.lastSegment().name shouldBe "par03AlphaScreen2"

        // One NavigateTo, two AbsoluteTargets, both regions already alive → both routed.
        sut.sendEvent(TestEvent("deeplinkBothWarm"))
        awaitItem().apply {
          regions[alphaRegionId]!!.active.lastSegment().name shouldBe "par03AlphaScreen" // moved back
          regions[betaRegionId]!!.active.lastSegment().name shouldBe "par03BetaScreen" // still there
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
            "par03App",
            "par03Alpha",
            "par03Beta",
          )
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("resolveRegionId resolves a schema-local id to its absolute region even when a sibling is deeper") {
      // test-18: DispatchBackTo carrying a leaf-module schema-local *RegionId constant must still
      // resolve to its absolute sub-region (via suffix-match) and route Back there, regardless of a
      // sibling having a deeper active stack.
      val schemaLocal = RegionId(Path("regionB"))
      val absoluteB = RegionId(Path("root", "regionB"))
      resolveRegionId(
        schemaLocal,
        setOf(RegionId(Path("root", "regionA")), absoluteB),
      ) shouldBe absoluteB
    }

    should("pruning a sub-region calls onExit on its screen before its flow (leaf-to-root)") {
      // test-19: when a parallel node's sub-region is pruned (e.g. the parent flow navigates away
      // from the parallel), the runtime must call onExit on the leaf screen before its containing
      // flow root, mirroring the leaf-to-root ordering of normal node-exit semantics.
      val exitLog = mutableListOf<String>()
      val appSchema = Parallel03Schema(
        par03MainSchema = Parallel03MainSchema(
          par03AlphaSchema = Parallel03AlphaSchema(),
          par03BetaSchema = Parallel03BetaSchema(),
        ),
      )
      val alphaNodeBuilder = Par03AlphaNodeBuilder(
        nodeFactory = object : Par03AlphaNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par03Alpha.par03AlphaScreen,
            onExitImpl = { exitLog.add("par03Alpha") },
          )
          override fun createPar03AlphaScreenNode(): ScreenNode = TestScreenNode(
            onExitImpl = { exitLog.add("par03AlphaScreen") },
          )
          override fun createPar03AlphaScreen2Node(): ScreenNode = TestScreenNode()
        },
        schema = Parallel03AlphaSchema(),
      )
      val betaNodeBuilder = Par03BetaNodeBuilder(
        nodeFactory = object : Par03BetaNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par03Beta.par03BetaScreen,
            onExitImpl = { exitLog.add("par03Beta") },
          )
          override fun createPar03BetaScreenNode(): ScreenNode = TestScreenNode(
            onExitImpl = { exitLog.add("par03BetaScreen") },
          )
        },
        schema = Parallel03BetaSchema(),
      )
      val mainNodeBuilder = Par03MainNodeBuilder(
        nodeFactory = object : Par03MainNodeBuilder.Factory {
          override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
          override fun createPar03AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
          override fun createPar03BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
        },
        schema = Parallel03MainSchema(Parallel03AlphaSchema(), Parallel03BetaSchema()),
      )
      val appNodeBuilder = Par03AppNodeBuilder(
        nodeFactory = object : Par03AppNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par03App.par03Main,
            transitions = listOf(tr("goToPage", Target.par03App.par03Page)),
          )
          override fun createPar03MainNodeBuilder(): NodeBuilder = mainNodeBuilder
          override fun createPar03PageNode(): ScreenNode = TestScreenNode()
        },
        schema = appSchema,
      )
      val sut = NavigationService<Unit>(
        nodeBuilder = appNodeBuilder,
        onFinishRequest = { Ignore },
      )

      sut.collectTransitions().test {
        awaitItem()
        sut.sendEvent(TestEvent("goToPage"))
        awaitItem()
        cancelAndIgnoreRemainingEvents()
      }

      // Within each pruned sub-region, the screen's onExit must be observed before the flow's onExit.
      exitLog.indexOf("par03AlphaScreen") shouldBeLessThan exitLog.indexOf("par03Alpha")
      exitLog.indexOf("par03BetaScreen") shouldBeLessThan exitLog.indexOf("par03Beta")
    }

    // A schema whose own root is `parallelFlow` (no outer flow wrapper)
    // must start cleanly via NavigationService.start without crashing in the build/check chain.
    // Before the SchemaCodegen restructure these crashed at `AppFlowNodeBuilder.build` with
    // "illegal path build requested ... appFlow.mainFlow" because schema.target returned a path
    // anchored at the SUB-region root instead of the schema root.
    should("top-level parallel-flow-rooted schema initializes both sub-regions on NavigationService.start") {
      val sut = buildTopRootService()

      sut.collectTransitions().test {
        awaitItem().apply {
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly("alpha", "beta")
          val alphaRegion = regions.entries.find { it.key.path.lastSegment().name == "alpha" }!!.value
          alphaRegion.active.lastSegment().name shouldBe "alphaScreen"
          val betaRegion = regions.entries.find { it.key.path.lastSegment().name == "beta" }!!.value
          betaRegion.active.lastSegment().name shouldBe "betaScreen"
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("top-level parallel-flow-rooted schema does not crash building the parallel root itself") {
      // The parallel-root path is `[topRoot]` (one segment). Before the codegen fix, the parent
      // NodeBuilder rejected this path with `path.firstSegment().id == rootPath.firstSegment().id`
      // because the parallel-root construction in NavigationService.start used a path whose first
      // segment didn't match the alias-anchored rootPath. Starting and reaching the first emission
      // exercises that build call.
      val sut = buildTopRootService()
      sut.collectTransitions().test {
        // If start() crashed, awaitItem() would propagate the exception instead of emitting.
        awaitItem()
        cancelAndIgnoreRemainingEvents()
      }
    }

    // Layout: `parallel -> app -> parallel -> (tabs)` — mirrors a real-world app's structure.
    //   acmeAppFlow (parallelFlow root)
    //     ├── acmeMainFlow (flow)
    //     │     └── acmeTabsFlow (parallelFlow, nested)
    //     │           ├── acmeHomeTab (flow) → acmeHomeScreen
    //     │           └── acmeExploreTab (flow) → acmeExploreScreen
    //     └── acmeAuthFlow (flow) → acmeAuthScreen
    // Exercises: top-level parallel root, LOCAL flow sub-region, nested parallel inside the
    // LOCAL flow, LOCAL flow children of the nested parallel — all in one tree.
    should("acme-style parallel→app→parallel→tabs initializes every sub-region down to the tabs") {
      val sut = buildAcmeTabsService()

      sut.collectTransitions().test {
        awaitItem().apply {
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
            "acmeMainFlow",
            "acmeAuthFlow",
            "acmeHomeTab",
            "acmeExploreTab",
          )
          val homeRegion = regions.entries.find { it.key.path.lastSegment().name == "acmeHomeTab" }!!.value
          homeRegion.active.lastSegment().name shouldBe "acmeHomeScreen"
          val exploreRegion = regions.entries.find { it.key.path.lastSegment().name == "acmeExploreTab" }!!.value
          exploreRegion.active.lastSegment().name shouldBe "acmeExploreScreen"
          val authRegion = regions.entries.find { it.key.path.lastSegment().name == "acmeAuthFlow" }!!.value
          authRegion.active.lastSegment().name shouldBe "acmeAuthScreen"
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("acme-style layout: each region has its own absolute regionId.path anchored at acmeAppFlow") {
      // Regression guard: under the codegen restructure, every region's absolute path
      // must include the schema root (acmeAppFlow) as its first segment AND the nested tabs
      // must be anchored under `acmeAppFlow.acmeMainFlow.acmeTabsFlow`. The pre-fix codegen
      // produced regionRoot-relative paths and misnested sub-regions.
      val sut = buildAcmeTabsService()

      sut.collectTransitions().test {
        awaitItem().apply {
          val regionPaths = regions.keys.map { it.path.toString() }.toSet()
          regionPaths shouldContain "acmeAppFlow.acmeMainFlow"
          regionPaths shouldContain "acmeAppFlow.acmeAuthFlow"
          regionPaths shouldContain "acmeAppFlow.acmeMainFlow.acmeTabsFlow.acmeHomeTab"
          regionPaths shouldContain "acmeAppFlow.acmeMainFlow.acmeTabsFlow.acmeExploreTab"
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    // Regression — the "top-up from Explore" cross-tab jump and its Back. Way has no single
    // transition that both pops a screen in one region AND switches the presented tab to another, so
    // the behaviour is composed on the tabs parallel node from primitives: the app writes its own
    // `currentTab` field (presentation) and uses EnqueueEvent to drive the target region. Back is
    // `NavigateTo(homeRoot) thenEnqueue <hop2>` where hop 2 switches the tab back to Explore AND
    // drives it to a target. The two hops are required: NavigateTo(homeRoot) must resolve while Home
    // is still current, so the tab switch is sequenced into a later dispatch via thenEnqueue. This locks the
    // recipe as library-verified end-to-end. Emissions: sendEvent drains its enqueued follow-ups
    // synchronously (default scheduler), each producing a listener notification, so
    // expectMostRecentItem() returns the final drained state.
    should("cross-tab: Back from a top-up jump pops it AND switches the app's tab to Explore, navigating to a target") {
      // The app owns which tab is presented. The tabs parallel holds its OWN `currentTab` field and
      // composes the cross-tab jump-and-return from primitives: `currentTab = ...` (presentation)
      // plus EnqueueEvent to drive the target region. Back is `NavigateTo(homeRoot) thenEnqueue
      // <hop2>`, so the tab switch is sequenced into a later dispatch after the pop resolves.
      val tabsNode = object : ParallelFlowNode<Unit>() {
        var currentTab: RegionId? = null
        var homeTabRegionId: RegionId? = null
        var exploreTabRegionId: RegionId? = null
        override val dismissResult = Unit
        override fun transition(event: Event): FlowTransition<Unit> = when {
          // Forward: user on Explore taps top-up → present Home + tell Home to open top-up.
          event is TestEvent && event.name == "openTopUp" -> {
            currentTab = homeTabRegionId
            EnqueueEvent(TestEvent("showTopUp"))
          }

          // Back hop 1: pop top-up in the Home region, then hand off to hop 2.
          event is TestEvent && event.name == "backFromTopUp" ->
            NavigateTo(Target.acmeHomeTab.acmeHomeScreen) thenEnqueue TestEvent("returnToExplore")

          // Back hop 2: present Explore AND navigate it to a target.
          event is TestEvent && event.name == "returnToExplore" -> {
            currentTab = exploreTabRegionId
            EnqueueEvent(TestEvent("showExploreDetail"))
          }

          else -> Ignore
        }
      }

      val sut = buildAcmeTabsService(
        homeTabTransitions = listOf(
          TestFlowTransitionSpec(
            eventMatcher = { it is TestEvent && it.name == "showTopUp" },
            transition = NavigateTo(Target.acmeHomeTab.acmeTopUpScreen),
          ),
        ),
        exploreTabTransitions = listOf(
          TestFlowTransitionSpec(
            eventMatcher = { it is TestEvent && it.name == "showExploreDetail" },
            transition = NavigateTo(Target.acmeExploreTab.acmeExploreDetailScreen),
          ),
        ),
        createTabsFlowNode = { tabsNode },
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val homeRegionId = initial.regions.keys.first { it.path.lastSegment().name == "acmeHomeTab" }
        val exploreRegionId = initial.regions.keys.first { it.path.lastSegment().name == "acmeExploreTab" }
        tabsNode.homeTabRegionId = homeRegionId
        tabsNode.exploreTabRegionId = exploreRegionId
        // Origin: the user is on Explore.
        tabsNode.currentTab = exploreRegionId

        initial.acmeActiveLeaf("acmeHomeTab") shouldBe "acmeHomeScreen"
        initial.acmeActiveLeaf("acmeExploreTab") shouldBe "acmeExploreScreen"

        // Forward: Explore → top-up (tab moves to Home, Home navigates onto top-up).
        sut.sendEvent(TestEvent("openTopUp"))
        expectMostRecentItem().apply {
          tabsNode.currentTab shouldBe homeRegionId
          acmeActiveLeaf("acmeHomeTab") shouldBe "acmeTopUpScreen"
          acmeActiveLeaf("acmeExploreTab") shouldBe "acmeExploreScreen" // sibling untouched
        }

        // Back: pop top-up in Home AND return the tab to Explore, navigating it to detail.
        sut.sendEvent(TestEvent("backFromTopUp"))
        expectMostRecentItem().apply {
          acmeActiveLeaf("acmeHomeTab") shouldBe "acmeHomeScreen" // top-up popped
          tabsNode.currentTab shouldBe exploreRegionId // tab back on Explore
          acmeActiveLeaf("acmeExploreTab") shouldBe "acmeExploreDetailScreen" // switch + navigate
          // No region pruned by the cross-tab pop.
          regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
            "acmeMainFlow",
            "acmeAuthFlow",
            "acmeHomeTab",
            "acmeExploreTab",
          )
        }

        cancelAndIgnoreRemainingEvents()
      }
    }

    // Variant of the recipe where hop 2 only RESTORES the app's tab to Explore (no navigation): a
    // direct `currentTab = explore` side-effect + `Stay`. Documents the "return to the origin tab
    // exactly as the user left it" form — Explore's own stack is untouched.
    should("cross-tab: Back from a top-up jump can restore Explore as-is (tab only, no navigation)") {
      val tabsNode = object : ParallelFlowNode<Unit>() {
        var currentTab: RegionId? = null
        var homeTabRegionId: RegionId? = null
        var exploreTabRegionId: RegionId? = null
        override val dismissResult = Unit
        override fun transition(event: Event): FlowTransition<Unit> = when {
          event is TestEvent && event.name == "openTopUp" -> {
            currentTab = homeTabRegionId
            EnqueueEvent(TestEvent("showTopUp"))
          }

          event is TestEvent && event.name == "backFromTopUp" ->
            NavigateTo(Target.acmeHomeTab.acmeHomeScreen) thenEnqueue TestEvent("returnToExplore")

          event is TestEvent && event.name == "returnToExplore" -> {
            currentTab = exploreTabRegionId // restore the presented tab, no navigation
            Stay
          }

          else -> Ignore
        }
      }

      val sut = buildAcmeTabsService(
        homeTabTransitions = listOf(
          TestFlowTransitionSpec(
            eventMatcher = { it is TestEvent && it.name == "showTopUp" },
            transition = NavigateTo(Target.acmeHomeTab.acmeTopUpScreen),
          ),
        ),
        createTabsFlowNode = { tabsNode },
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        val homeRegionId = initial.regions.keys.first { it.path.lastSegment().name == "acmeHomeTab" }
        val exploreRegionId = initial.regions.keys.first { it.path.lastSegment().name == "acmeExploreTab" }
        tabsNode.homeTabRegionId = homeRegionId
        tabsNode.exploreTabRegionId = exploreRegionId
        tabsNode.currentTab = exploreRegionId

        sut.sendEvent(TestEvent("openTopUp"))
        expectMostRecentItem().acmeActiveLeaf("acmeHomeTab") shouldBe "acmeTopUpScreen"

        sut.sendEvent(TestEvent("backFromTopUp"))
        expectMostRecentItem().apply {
          acmeActiveLeaf("acmeHomeTab") shouldBe "acmeHomeScreen" // top-up popped
          tabsNode.currentTab shouldBe exploreRegionId // tab restored
          acmeActiveLeaf("acmeExploreTab") shouldBe "acmeExploreScreen" // Explore left exactly as-is
        }

        cancelAndIgnoreRemainingEvents()
      }
    }

    // Validates the canonical SCXML building blocks (StatechartAlgorithm.kt) against a REAL generated
    // schema + REAL absolute paths from a running service — bridging the isolated unit tests
    // (StatechartAlgorithmTest, which use a hand-rolled nodeTypeOf lambda) to the live schema/codegen.
    // This is the equivalence anchor for rewiring the resolver through findLCCA/computeExitSet.
    should("SCXML findLCCA/computeExitSet/getTransitionDomain agree with the live acme-tabs configuration") {
      val schema = ParallelTestAcmeTabsSchema()
      val nodeTypeOf: (Path) -> Schema.NodeType = { findNodeType(schema, it) }
      val sut = buildAcmeTabsService()

      sut.collectTransitions().test {
        val state = awaitItem()
        val homeLeaf = state.regions.entries.first { it.key.path.lastSegment().name == "acmeHomeTab" }.value.active
        val exploreLeaf = state.regions.entries.first {
          it.key.path.lastSegment().name == "acmeExploreTab"
        }.value.active
        val configuration = state.regions.values.flatMap { it.alive }

        // The two tabs' nearest common ancestor is acmeTabsFlow (a ParallelFlow); SCXML excludes a
        // parallel from being an LCCA, so it lifts to the enclosing compound flow acmeMainFlow.
        val lcca = findLCCA(listOf(homeLeaf, exploreLeaf), nodeTypeOf)
        lcca.lastSegment().name shouldBe "acmeMainFlow"

        // An atomic (Screen) source cannot be an internal-transition domain (it has no descendants
        // to stay within), so the domain of a self-target from the home screen is the LCCA — the
        // enclosing Home flow. This keeps the transition scoped to Home; sibling regions are untouched.
        val homeDomain = getTransitionDomain(homeLeaf, listOf(homeLeaf), isInternal = true, nodeTypeOf)
        homeDomain?.lastSegment()?.name shouldBe "acmeHomeTab"

        // Exiting at acmeMainFlow tears down BOTH tab subtrees but leaves the orthogonal acmeAuthFlow
        // region (not a descendant of acmeMainFlow) untouched — the load-bearing LCCA-scoping property.
        val exitSet = computeExitSet(lcca, configuration)
        exitSet.any { it.lastSegment().name == "acmeHomeScreen" } shouldBe true
        exitSet.any { it.lastSegment().name == "acmeExploreScreen" } shouldBe true
        exitSet.any { it.lastSegment().name == "acmeAuthScreen" } shouldBe false
        // Leaf-first ordering: no path in the exit set precedes one of its own ancestors.
        exitSet shouldBe exitSet.sortedByDescending { it.length }

        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── A1. Parallel-root detection (NavigationService.kt:169-196) ────────────────────────────────
    // Regression — a real-world shape (`appFlow(parallel) → mainFlow → homeFlow(parallel)`) requires
    // the parallel-root init branch to fire correctly even when a parallel-rooted schema is mounted
    // INSIDE another parallel-rooted schema. Today only the outermost is handled by the init branch
    // (NavigationService.kt:173-196 with the `rootIsParallelFlow` check + `require(rootNode is
    // ParallelFlowNode<*>)`); sub-region roots go through `require(regionRoot is FlowNode<*>)` at
    // line 214 which rejects a `ParallelFlowNode<*>` returned by a nested parallel-rooted builder.
    //
    // This test locks in the expected end state: both `ParallelFlowNode` instances are constructed
    // and reachable from `state`, and `state.rootNode` references the OUTERMOST parallel.
    // Fixture: `parallel-test-nested-root.dot` wraps the parallel-rooted
    // `parallel-test-nested-inner.dot` as its single sub-region.
    should(
      "parallel-flow root nested inside another parallel-flow root builds both ParallelFlowNodes via the parallel-root init branch and state.rootNode points at the outermost",
    ) {
      val outerRootNode = TestParallelNode()
      val innerRootNode = TestParallelNode()
      val sut = buildNestedRootService(
        createOuterRoot = { outerRootNode },
        createInnerRoot = { innerRootNode },
      )

      sut.collectTransitions().test {
        val initial = awaitItem()

        // Outer parallel root is recorded as state.rootNode — the runtime entered the OUTERMOST
        // parallel-flow via the parallel-root init branch (NavigationService.kt:178-196).
        initial.rootNode shouldBe outerRootNode

        // Both inner sub-regions (nestedAlpha, nestedBeta) are alive — proving the inner
        // parallel-flow was also built and its own sub-regions were materialised.
        initial.regions.keys.map { it.path.lastSegment().name }.toSet() shouldBe setOf(
          "nestedAlpha",
          "nestedBeta",
        )
        val alphaRegion = initial.regions.entries.find { it.key.path.lastSegment().name == "nestedAlpha" }!!.value
        alphaRegion.active.lastSegment().name shouldBe "nestedAlphaScreen"
        val betaRegion = initial.regions.entries.find { it.key.path.lastSegment().name == "nestedBeta" }!!.value
        betaRegion.active.lastSegment().name shouldBe "nestedBetaScreen"

        cancelAndIgnoreRemainingEvents()
      }
    }

    // Defensive — `firstOrNull()` at NavigationService.kt:170 already makes a zero-regions schema
    // fall through cleanly (rootIsParallelFlow = false, regions loop runs zero iterations). Lock
    // that contract: InitEvent leaves `state.rootNode` null and the service does not crash on
    // start when the schema has no regions to materialise.
    should("schema with zero regions does not crash — InitEvent rootNode stays null") {
      val emptyRegionsSchema = object : Schema {
        override val rootSegment: Segment = Segment("empty")
        override val childSchemas: Map<Segment, Schema> = emptyMap()
        override val regions: List<RegionId> = emptyList()
        override fun target(regionId: RegionId, segment: Segment, rootSegmentAlias: Segment?): Path? = null
        override fun nodeType(regionId: RegionId, path: Path, rootSegmentAlias: Segment?): Schema.NodeType =
          error("nodeType must not be called for an empty-regions schema in this test")
        override fun createChildFlowFinishRequestEvent(regionId: RegionId, path: Path, result: Any): Event =
          error("createChildFlowFinishRequestEvent must not be called for an empty-regions schema in this test")
      }
      // TestNodeBuilder's build mapping is empty — the runtime must NOT invoke build at all for a
      // schema with no regions and no parallel-flow root.
      val sut = NavigationService<Unit>(
        nodeBuilder = TestNodeBuilder(schema = emptyRegionsSchema, mapping = emptyMap()),
        onFinishRequest = { Stay },
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        initial.rootNode shouldBe null
        initial.regions.isEmpty() shouldBe true
        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── A2. Sub-region Finish routing for parallel-rooted top-level (NavigationService.kt:226-237)
    // The `regionRootPath.length == 1 ? onFinishRequest : computeSubRegionFinishBuilder` discriminator
    // is new. For a parallel-rooted TOP-LEVEL schema whose sub-region has `resultType != Unit`,
    // emitting `Finish(result)` from the sub-region MUST route through computeSubRegionFinishBuilder
    // so the parent parallel-flow sees a typed `ChildFinishRequest` — NOT through onFinishRequest
    // (which would silently receive a wrongly-typed Any of the sub-region's R, breaking the
    // top-level service's `<R>` contract).
    //
    // Fixture: `parallel-test-top-root-finish.dot` — `rootParallel [parallelFlow]` with
    // `childFinish [flow, resultType = "kotlin.Int"]` and `childOther [flow]` siblings.
    should(
      "sub-region Finish in parallel-rooted top-level schema routes to parent ParallelFlowNode via ChildFinishRequest, NOT to onFinishRequest",
    ) {
      var onFinishRequestInvoked = false
      val parallelReceivedEvents = mutableListOf<Event>()
      val rootParallelNode = object : ParallelFlowNode<Unit>() {
        override val dismissResult = Unit
        override fun transition(event: Event): FlowTransition<Unit> {
          parallelReceivedEvents.add(event)
          return Ignore
        }
      }
      val sut = buildTopRootFinishService(
        createRootParallel = { rootParallelNode },
        // childFinish sub-region emits Finish(42) when it receives TestEvent("finishMe")
        childFinishTransitions = listOf(
          TestFlowTransitionSpec(
            eventMatcher = { it is TestEvent && it.name == "finishMe" },
            transition = Finish(42),
          ),
        ),
        onFinishRequest = {
          onFinishRequestInvoked = true
          Ignore
        },
      )

      sut.collectTransitions().test {
        awaitItem() // initial
        sut.sendEvent(TestEvent("finishMe"))
        // Finish → computeSubRegionFinishBuilder → EnqueueEvent(RootParallelChildFinishRequest.ChildFinish(42))
        awaitItem()
        awaitItem() // child-finish drained → parallel Ignore
        cancelAndIgnoreRemainingEvents()
      }

      // The parent parallel-flow received a typed ChildFinishRequest carrying the Int result —
      // proves computeSubRegionFinishBuilder was the routing path (the `length == 1` branch
      // would have invoked onFinishRequest with the raw Int instead).
      val childFinishEvent = parallelReceivedEvents
        .filterIsInstance<RootParallelChildFinishRequest.ChildFinish>()
        .singleOrNull()
      childFinishEvent shouldBe RootParallelChildFinishRequest.ChildFinish(42)
      // And the service-level onFinishRequest was NOT consulted — proves the discriminator at
      // NavigationService.kt:226 correctly preferred the sub-region path.
      onFinishRequestInvoked shouldBe false
    }

    // ── A3. Intermediate parallels observe events (Fix 2)
    // When a leaf region's Finish bubbles up via computeSubRegionFinishBuilder, the resulting
    // ChildFinishRequest is typed for the NEAREST enclosing parallel — which is the INTERMEDIATE
    // `nestedInner` parallel in this fixture, NOT the outermost root `nestedOuter`. Before Fix 2,
    // the runtime only dispatched events through (a) every leaf region's active node and
    // (b) `state.rootNode`. The intermediate parallel was never asked, so the typed
    // ChildFinishRequest silently died — no one's `transition()` saw it.
    should(
      "ChildFinishRequest from leaf region reaches intermediate parallel's transition() (NOT only the root parallel)",
    ) {
      val outerEvents = mutableListOf<Event>()
      val innerEvents = mutableListOf<Event>()
      val outerParallel = TestParallelNode(onTransitionCallback = { outerEvents.add(it) })
      val innerParallel = TestParallelNode(onTransitionCallback = { innerEvents.add(it) })
      val sut = buildNestedRootService(
        createOuterRoot = { outerParallel },
        createInnerRoot = { innerParallel },
        nestedAlphaTransitions = listOf(
          TestFlowTransitionSpec(
            eventMatcher = { it is TestEvent && it.name == "finishAlpha" },
            transition = Finish(Unit),
          ),
        ),
      )

      sut.collectTransitions().test {
        awaitItem() // initial
        sut.sendEvent(TestEvent("finishAlpha"))
        // Drain: (1) leaf Finish emits EnqueueEvent(NestedInnerChildFinishRequest.NestedAlpha)
        // (2) the child-finish event is dispatched to the inner parallel via the intermediates
        //     walk in resolveTransition (new Fix 2 behaviour).
        awaitItem()
        awaitItem()
        cancelAndIgnoreRemainingEvents()
      }

      // The INTERMEDIATE inner parallel must observe the typed ChildFinishRequest emitted by the
      // leaf nestedAlpha flow's Finish. This is the core proof for Fix 2.
      val innerChildFinish = innerEvents
        .filterIsInstance<NestedInnerChildFinishRequest>()
        .singleOrNull()
      innerChildFinish shouldBe NestedInnerChildFinishRequest.NestedAlpha

      // Every parallel observes EVERY event in a dispatch cycle — the outer's transition() WILL
      // be called with `NestedInnerChildFinishRequest.NestedAlpha`, but that event's TYPE is
      // owned by the inner schema, so the outer cannot pattern-match on it and ignores it.
      // The meaningful invariant is that the outer did NOT see its OWN typed
      // `NestedOuterChildFinishRequest` — that would only fire if the INNER parallel itself
      // emitted Finish in response, which we are not testing here.
      outerEvents.none { it is NestedOuterChildFinishRequest } shouldBe true
    }

    // ── A4. NavigateTo from a root parallel routes to the named region (Fix 3)
    // Before Fix 3, `is NavigateTo -> ResolvedTransition.EMPTY` in resolveRootParallelInner
    // silently dropped any NavigateTo returned from a parallel's transition(). With Fix 2
    // opening up the same code path for intermediates, this no-op became a footgun. Fix 3
    // delegates NavigateTo from a parallel to resolveTransitionInRegion via a synthetic
    // single-node region — both AbsoluteTarget and FlowTarget/ScreenTarget routes work.
    should("NavigateTo(AbsoluteTarget) returned from a root parallel routes to the named region") {
      // The root parallel returns NavigateTo(AbsoluteTarget(...beta.betaScreen)) when it sees the
      // trigger event. Without Fix 3 this would be a silent no-op and would not invoke
      // resolveTransitionInRegion at all. Here we use the topRoot schema and compute the
      // beta region's absolute screen path via `Schema.target` to avoid hardcoding segment IDs.
      val triggerEvent = TestEvent("navToBeta")
      val schema = ParallelTestTopRootSchema()
      val betaRegionId = schema.regions.first { it.path.lastSegment().name == "beta" }
      val betaScreenSegment = Target.beta.betaScreen.path.firstSegment()
      val betaScreenAbsPath = schema.target(betaRegionId, betaScreenSegment)
        ?: error("schema.target returned null for $betaScreenSegment")
      val rootParallelNode = TestParallelNode(
        parallelTransitions = listOf(
          TestParallelTransitionSpec(
            eventMatcher = { it == triggerEvent },
            transition = NavigateTo(AbsoluteTarget(betaScreenAbsPath)),
          ),
        ),
      )
      val sut = buildTopRootServiceWithCustomRoot(createRootParallel = { rootParallelNode })

      sut.collectTransitions().test {
        val initial = awaitItem()
        val initialBeta = initial.regions.entries
          .first { it.key.path.lastSegment().name == "beta" }.value
        initialBeta.active.lastSegment().name shouldBe "betaScreen"

        sut.sendEvent(triggerEvent)
        // The NavigateTo dispatch from the root parallel must NOT throw, and the beta region's
        // active path must include the betaScreen. (We use the region's initial screen as the
        // navigation target since this fixture only has a single screen per region — the
        // meaningful test is that NavigateTo from a parallel is no longer a no-op and reaches
        // resolveTransitionInRegion via the synthetic single-node region, proved by the
        // absence of an exception + the regions still being well-formed afterwards.)
        val next = awaitItem()
        val nextBeta = next.regions.entries
          .first { it.key.path.lastSegment().name == "beta" }.value
        nextBeta.active.lastSegment().name shouldBe "betaScreen"
        // The alpha region was not navigated away from either — the NavigateTo only mentioned
        // beta, which lines up with the per-region nature of the resolution.
        val nextAlpha = next.regions.entries
          .first { it.key.path.lastSegment().name == "alpha" }.value
        nextAlpha.active.lastSegment().name shouldBe "alphaScreen"

        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── Runtime mount of an intermediate parallel-rooted sub-region ─────────────────
    // Outer schema (parallel-test-lazy-intermediate.dot) is a flow whose initial child is a screen;
    // its other child is an imported parallel-rooted schema. The intermediate parallel at
    // `outerApp.importedParallel` is NOT pre-mounted at Init — only `outerScreen` is alive.
    // Driving NavigateTo to a sub-region under the intermediate forces the new runtime pre-mount
    // step in NavigationService.transition to (a) build + enter the intermediate parallel, (b)
    // register it in `_intermediateParallels` so calculateAliveNodes' retainAll keeps the
    // freshly-activated leaf regions. A follow-up NavigateTo back to outerScreen drives the
    // pre-unmount step.
    should(
      "NavigateTo into a sub-region under a not-yet-mounted intermediate parallel-rooted schema materializes the intermediate and routes correctly",
    ) {
      var leftScreenAbsPath: Path? = null
      val intermediateOnEntryCount = mutableListOf<String>()
      val intermediateOnExitCount = mutableListOf<String>()
      val intermediateNode = TestParallelNode(
        onEntryImpl = { intermediateOnEntryCount.add("entered") },
        onExitImpl = { intermediateOnExitCount.add("exited") },
      )

      val sut = buildLazyIntermediateService(
        createOuterApp = {
          object : FlowNode<Unit> {
            override val initial: Target = Target.outerApp.outerScreen
            override val dismissResult = Unit
            override fun transition(event: Event): FlowTransition<Unit> = when {
              event is TestEvent && event.name == "deeplink" ->
                leftScreenAbsPath?.let { NavigateTo(AbsoluteTarget(it)) } ?: Ignore

              event is TestEvent && event.name == "back" ->
                NavigateTo(Target.outerApp.outerScreen)

              else -> Ignore
            }
          }
        },
        createImportedParallel = { intermediateNode },
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        // Pre-state: only the outer flow region with outerScreen alive. No intermediate yet.
        initial.regions.keys.map { it.path.lastSegment().name }.shouldContainOnly("outerApp")
        val outerRegion = initial.regions.entries.first { it.key.path.lastSegment().name == "outerApp" }
        outerRegion.value.active.lastSegment().name shouldBe "outerScreen"
        initial._intermediateParallels.isEmpty() shouldBe true
        intermediateOnEntryCount.isEmpty() shouldBe true

        // Compute the absolute path to leftScreen using the SAME schema instance the service
        // sees — mirrors the pattern at ParallelNodeTest.kt:2230-2234 for partoproot deep links.
        val outerSchema = ParallelTestLazyIntermediateSchema(
          importedParallelSchema = ParallelTestLazyIntermediateInnerSchema(),
        )
        val outerAppRegionId = outerSchema.regions.first { it.path.lastSegment().name == "outerApp" }
        val leftScreenSegment = Target.leftTab.leftScreen.path.firstSegment()
        // The outer schema's `target` table only knows about its own segments (outerScreen,
        // importedParallel). For inner-schema segments we have to look them up via the INNER
        // schema and prepend the outer schema's regionRoot path. Simpler: build the absolute
        // path explicitly from the rootSegment + intermediate + leftTab + leftScreen.
        val innerSchema = ParallelTestLazyIntermediateInnerSchema()
        val intermediateSegment = outerSchema.childSchemas.keys.first()
        val leftTabSegment = innerSchema.childSchemas.keys.first { it.name == "leftTab" }
        leftScreenAbsPath = Path(
          listOf(outerSchema.rootSegment, intermediateSegment, leftTabSegment, leftScreenSegment),
        )

        sut.sendEvent(TestEvent("deeplink"))
        val afterMount = awaitItem()

        // The intermediate parallel was mounted at runtime: now in _intermediateParallels and
        // its onEntry fired exactly once.
        afterMount._intermediateParallels.keys.map { it.toString() }.toSet().shouldContainOnly(
          Path(listOf(outerSchema.rootSegment, intermediateSegment)).toString(),
        )
        intermediateOnEntryCount.size shouldBe 1

        // Both leaf sub-regions are alive at their absolute paths; leftTab routes to leftScreen
        // (the explicit target) while rightTab initialises to its default (the schema's initial).
        afterMount.regions.keys.map { it.path.lastSegment().name }.shouldContainOnly(
          "outerApp",
          "leftTab",
          "rightTab",
        )
        val leftTabRegion = afterMount.regions.entries.first { it.key.path.lastSegment().name == "leftTab" }
        leftTabRegion.value.active.lastSegment().name shouldBe "leftScreen"
        val rightTabRegion = afterMount.regions.entries.first { it.key.path.lastSegment().name == "rightTab" }
        rightTabRegion.value.active.lastSegment().name shouldBe "rightScreen"

        // Navigate back: the pre-unmount step in transition() must drop the intermediate and
        // fire its onExit. retainAll then prunes leftTab/rightTab because their parallelParent
        // is no longer in _intermediateParallels.
        sut.sendEvent(TestEvent("back"))
        val afterBack = awaitItem()
        afterBack._intermediateParallels.isEmpty() shouldBe true
        intermediateOnExitCount.size shouldBe 1
        afterBack.regions.keys.map { it.path.lastSegment().name }.shouldContainOnly("outerApp")
        afterBack.regions.entries.first { it.key.path.lastSegment().name == "outerApp" }
          .value.active.lastSegment().name shouldBe "outerScreen"

        cancelAndIgnoreRemainingEvents()
      }
    }

    // Compensation test: a leaf flow's onEntry throws AFTER the runtime pre-mount step has
    // already mounted the intermediate. The synchronizeNodes catch + outer-catch rollback must
    // leave `_intermediateParallels` exactly as it was before the failed transition (empty here).
    // Mirrors the pattern in SnapshotRollbackTest.kt — throw-injection via TestNodeBuilder is
    // not usable here because the production NodeBuilder is generated; instead we inject the
    // throw at the leaf flow's onEntry, which fires inside synchronizeNodes' per-region build
    // loop and routes through the same sync-catch + outer-catch as a build throw.
    should(
      "Failed transition that mounted intermediate parallel rolls back via _intermediateParallels snapshot",
    ) {
      var leftScreenAbsPath: Path? = null
      val intermediateOnEntryCount = mutableListOf<String>()
      val intermediateOnExitCount = mutableListOf<String>()
      val intermediateNode = TestParallelNode(
        onEntryImpl = { intermediateOnEntryCount.add("entered") },
        onExitImpl = { intermediateOnExitCount.add("exited") },
      )

      val sut = buildLazyIntermediateService(
        createOuterApp = {
          object : FlowNode<Unit> {
            override val initial: Target = Target.outerApp.outerScreen
            override val dismissResult = Unit
            override fun transition(event: Event): FlowTransition<Unit> = when {
              event is TestEvent && event.name == "deeplink" ->
                leftScreenAbsPath?.let { NavigateTo(AbsoluteTarget(it)) } ?: Ignore

              else -> Ignore
            }
          }
        },
        createImportedParallel = { intermediateNode },
        leftTabRootFactory = {
          // Throws inside synchronizeNodes' per-region build loop, AFTER the runtime pre-mount
          // step entered the intermediate. The sync-catch must compensate the intermediate's
          // onEntry (so onExit fires) and the outer catch must restore _intermediateParallels
          // to its pre-transition snapshot.
          TestFlowNode(
            initialTarget = Target.leftTab.leftScreen,
            onEntryImpl = { error("injected throw at leftTab root onEntry") },
          )
        },
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        initial._intermediateParallels.isEmpty() shouldBe true

        val outerSchema = ParallelTestLazyIntermediateSchema(
          importedParallelSchema = ParallelTestLazyIntermediateInnerSchema(),
        )
        val innerSchema = ParallelTestLazyIntermediateInnerSchema()
        val intermediateSegment = outerSchema.childSchemas.keys.first()
        val leftTabSegment = innerSchema.childSchemas.keys.first { it.name == "leftTab" }
        val leftScreenSegment = Target.leftTab.leftScreen.path.firstSegment()
        leftScreenAbsPath = Path(
          listOf(outerSchema.rootSegment, intermediateSegment, leftTabSegment, leftScreenSegment),
        )

        shouldThrow<Throwable> { sut.sendEvent(TestEvent("deeplink")) }

        // The state-after-throw should be UNCHANGED: only outerApp region with outerScreen,
        // and _intermediateParallels empty (restored from snapshot by the outer catch).
        val afterThrow = sut.snapshotForTest()
        afterThrow._intermediateParallels.isEmpty() shouldBe true
        afterThrow.regions.keys.map { it.path.lastSegment().name }.shouldContainOnly("outerApp")
        afterThrow.regions.entries.first { it.key.path.lastSegment().name == "outerApp" }
          .value.active.lastSegment().name shouldBe "outerScreen"

        // Lifecycle balance: the intermediate received onEntry from the pre-mount step and
        // a matching onExit from the sync-catch's `entered.reversed().callOnExit` sweep.
        intermediateOnEntryCount.size shouldBe 1
        intermediateOnExitCount.size shouldBe 1

        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── Fix #2: NavigateTo(FlowTarget | ScreenTarget) from a parallel resolves against the
    // focused sub-region's schema. Before the fix, the synthetic-region delegation passed
    // `activePath = parallelNodePath`, so resolveAbsoluteTargetPath landed on the parallel's
    // parent schema and silently fell back to `regions.first()`.
    should("NavigateTo(FlowTarget) from a root parallel resolves against the first declared sub-region's schema") {
      // NavigateTo(FlowTarget("parrelfAlphaInner")) from the root parallel. The imported
      // `parrelfAlphaInner` sub-schema lives ONLY in alpha's child schema; the root parallel's
      // schema does NOT know it. A relative FlowTarget from a parallel resolves against the FIRST
      // declared sub-region (alpha) — whose schema (ParallelTestRelFocusedAlphaSchema) DOES know
      // `parrelfAlphaInner` — and navigation lands inside alpha's tree at the inner flow's initial
      // screen, rather than the root schema's `regions.first()` fallback throwing.
      val triggerEvent = TestEvent("navToAlphaInner")
      // FlowTarget for the inner flow (parrelfAlphaInner). The relative path is just its root
      // segment; the runtime appends it under the first sub-region's tree.
      val alphaInnerFlowTargetPath = Path(
        Segment(
          "parrelfAlphaInner@ParallelTestRelFocusedAlphaInner:" +
            "src/commonTest/way/parallel-test-relfocused-alpha-inner.dot",
        ),
      )
      val rootParallelNode = TestParallelNode(
        parallelTransitions = listOf(
          TestParallelTransitionSpec(
            eventMatcher = { it == triggerEvent },
            transition = NavigateTo(FlowTarget(alphaInnerFlowTargetPath)),
          ),
        ),
      )
      val sut = buildRelFocusedServiceWithCustomRoot { rootParallelNode }

      sut.collectTransitions().test {
        awaitItem() // initial
        sut.sendEvent(triggerEvent)
        val next = awaitItem()
        val nextAlpha = next.regions.entries.first { it.key.path.lastSegment().name == "parrelfAlpha" }.value
        // Navigation landed inside alpha's tree at the inner flow's initial screen.
        nextAlpha.active.toString() shouldBe
          "parrelfRoot.parrelfAlpha.parrelfAlphaIntro.parrelfAlphaInner.parrelfAlphaInnerScreen"
        // Beta is untouched.
        val nextBeta = next.regions.entries.first { it.key.path.lastSegment().name == "parrelfBeta" }.value
        nextBeta.active.lastSegment().name shouldBe "parrelfBetaIntro"
      }
    }

    should("NavigateTo(AbsoluteTarget) from a root parallel routes to a non-first sub-region's sibling screen") {
      // To target a SPECIFIC (here non-first) sub-region rather than the first-declared fallback,
      // the app uses an AbsoluteTarget. AbsoluteTarget(parrelfRoot.parrelfBeta.parrelfBetaDetail)
      // routes into beta's tree at its detail screen; alpha (the first sub-region) is untouched.
      val triggerEvent = TestEvent("navToBetaDetail")
      val betaDetailSegment = Segment(
        "parrelfBetaDetail@ParallelTestRelFocusedBeta:" +
          "src/commonTest/way/parallel-test-relfocused-beta.dot",
      )
      // The AbsoluteTarget's path is captured after start() once the absolute beta path is known.
      var betaDetailAbsPath: Path? = null
      val sut = buildRelFocusedServiceWithCustomRoot {
        object : ParallelFlowNode<Unit>() {
          override val dismissResult = Unit
          override fun transition(event: Event): FlowTransition<Unit> = if (event == triggerEvent) {
            NavigateTo(AbsoluteTarget(requireNotNull(betaDetailAbsPath) { "capture betaDetailAbsPath first" }))
          } else {
            Ignore
          }
        }
      }

      sut.collectTransitions().test {
        val initial = awaitItem()
        val betaRegionId = initial.regions.keys.first { it.path.lastSegment().name == "parrelfBeta" }
        betaDetailAbsPath = betaRegionId.path.append(Path(betaDetailSegment))
        sut.sendEvent(triggerEvent)
        val next = awaitItem()
        val nextBeta = next.regions.entries.first { it.key.path.lastSegment().name == "parrelfBeta" }.value
        nextBeta.active.toString() shouldBe "parrelfRoot.parrelfBeta.parrelfBetaDetail"
        val nextAlpha = next.regions.entries.first { it.key.path.lastSegment().name == "parrelfAlpha" }.value
        nextAlpha.active.lastSegment().name shouldBe "parrelfAlphaIntro"
      }
    }

    should(
      "NavigateTo(FlowTarget) from a parallel resolves against the first declared sub-region",
    ) {
      // A relative FlowTarget from a parallel resolves against the FIRST sub-region in subRegionIds
      // (alpha). FlowTarget(parrelfAlphaInner) resolves through alpha's schema; the inner flow's
      // initial chains to its leaf screen.
      val triggerEvent = TestEvent("navToAlphaInnerNoFocus")
      val alphaInnerFlowTargetPath = Path(
        Segment(
          "parrelfAlphaInner@ParallelTestRelFocusedAlphaInner:" +
            "src/commonTest/way/parallel-test-relfocused-alpha-inner.dot",
        ),
      )
      val rootParallelNode = TestParallelNode(
        parallelTransitions = listOf(
          TestParallelTransitionSpec(
            eventMatcher = { it == triggerEvent },
            transition = NavigateTo(FlowTarget(alphaInnerFlowTargetPath)),
          ),
        ),
      )
      val sut = buildRelFocusedServiceWithCustomRoot { rootParallelNode }
      // Exercises the `subRegionIds.firstOrNull()` first-sub-region resolution.

      sut.collectTransitions().test {
        awaitItem() // initial
        sut.sendEvent(triggerEvent)
        val next = awaitItem()
        val nextAlpha = next.regions.entries.first { it.key.path.lastSegment().name == "parrelfAlpha" }.value
        nextAlpha.active.toString() shouldBe
          "parrelfRoot.parrelfAlpha.parrelfAlphaIntro.parrelfAlphaInner.parrelfAlphaInnerScreen"
        val nextBeta = next.regions.entries.first { it.key.path.lastSegment().name == "parrelfBeta" }.value
        nextBeta.active.lastSegment().name shouldBe "parrelfBetaIntro"
      }
    }

    // ── Fix #3: cross-region NavigateTo through an unmounted intermediate parallel must not
    // pollute the source region's alive list. Even when the synthetic case where line 787 fires
    // is rare, the guard keeps the resolution layer defensive against future call sites and
    // documents the invariant.
    should(
      "NavigateTo(AbsoluteTarget) from a screen in one region into a sibling region's path under an unmounted intermediate-parallel-rooted sub-schema does not corrupt the source region's alive list",
    ) {
      // Schema: parallel root with sibling flows alpha + beta; beta has a sibling import to a
      // parallel-rooted schema that's NOT pre-mounted at Init (beta's initial is parcriBetaIntro).
      // From parcriAlpha's screen, NavigateTo into [parcriRoot, parcriBeta, parcriBetaImported,
      // parcriBetaLeft, parcriBetaLeftScreen]. The intermediate mounts at runtime.
      val deeplinkEvent = TestEvent("deeplinkAlphaToBetaImported")
      val intermediateOnEntry = mutableListOf<String>()
      val intermediateNode = TestParallelNode(
        onEntryImpl = { intermediateOnEntry.add("entered") },
      )

      // Build the absolute target path explicitly via segment lookup so the test does not
      // hardcode @file suffixes.
      val outerSchema = ParallelTestCrossRegionIntermediateSchema(
        parcriBetaImportedSchema = ParallelTestCrossRegionIntermediateInnerSchema(),
      )
      val innerSchema = ParallelTestCrossRegionIntermediateInnerSchema()
      val rootSegment = outerSchema.rootSegment
      val betaSegment = outerSchema.regions.first { it.path.lastSegment().name == "parcriBeta" }
        .path.lastSegment()
      val importedSegment = outerSchema.childSchemas.keys
        .first { it.name == "parcriBetaImported" }
      val leftTabSegment = innerSchema.childSchemas.keys.first { it.name == "parcriBetaLeft" }
      val leftScreenSegment = Target.parcriBetaLeft.parcriBetaLeftScreen.path.firstSegment()
      val leftScreenAbs = Path(
        listOf(rootSegment, betaSegment, importedSegment, leftTabSegment, leftScreenSegment),
      )

      val sut = buildCrossRegionIntermediateService(
        createImported = { intermediateNode },
        alphaScreenTransitions = listOf(
          TestScreenTransitionSpec(
            eventMatcher = { it == deeplinkEvent },
            transition = NavigateTo(AbsoluteTarget(leftScreenAbs)),
          ),
        ),
      )

      sut.collectTransitions().test {
        val initial = awaitItem()
        // Pre-state: alpha + beta alive, no intermediate mounted.
        initial.regions.keys.map { it.path.lastSegment().name }.toSet().shouldContainOnly(
          "parcriAlpha",
          "parcriBeta",
        )
        val initialAlpha = initial.regions.entries.first { it.key.path.lastSegment().name == "parcriAlpha" }
        initialAlpha.value.active.lastSegment().name shouldBe "parcriAlphaScreen"
        val initialAlphaAlive = initialAlpha.value.alive.toList()
        initial._intermediateParallels.isEmpty() shouldBe true
        intermediateOnEntry.isEmpty() shouldBe true

        sut.sendEvent(deeplinkEvent)
        val after = awaitItem()

        // Source region alpha is unchanged.
        val afterAlpha = after.regions.entries.first { it.key.path.lastSegment().name == "parcriAlpha" }
        afterAlpha.value.active shouldBe initialAlpha.value.active
        afterAlpha.value.alive.toList() shouldBe initialAlphaAlive

        // Intermediate parallel is mounted.
        after._intermediateParallels.keys.map { it.toString() }.toSet().shouldContainOnly(
          Path(listOf(rootSegment, betaSegment, importedSegment)).toString(),
        )
        intermediateOnEntry.size shouldBe 1

        // Target sub-region is alive at the requested leaf path.
        val leftRegion = after.regions.entries.first { it.key.path.lastSegment().name == "parcriBetaLeft" }
        leftRegion.value.active shouldBe leftScreenAbs

        cancelAndIgnoreRemainingEvents()
      }
    }

    // Mirrors a real-world app's exact appFlow [parallelFlow] → mainFlowImport [flow] → homeImport [parallelFlow]
    // arrangement. Init only mounts the two top-level regions (mainFlowImport + siblingSheet) plus
    // the outer parallel root; mainFlowImport's initial child is mainScreen (a screen) so the
    // intermediate `homeImport` parallel is NOT pre-mounted. A NavigateTo from mainScreen into
    // homeImport.tabA.tabAScreen forces the runtime mount step in NavigationService.transition to
    // (a) build + enter homeImport, (b) materialise tabA and tabB. A typed Finish from inside tabA
    // bubbles HomeImportChildFinishRequest.TabA into homeImport's transition() — NOT into the
    // outer mainFlowImport flow (proves the child-finish event is typed for the inner schema only).
    // Navigating back to mainScreen drives the pre-unmount step.
    should(
      "acme-shape: parallelFlow root → flow region → NavigateTo into imported parallel-rooted " +
        "sub-schema mounts the intermediate and routes correctly",
    ) {
      var tabAScreenAbsPath: Path? = null
      val homeImportOnEntry = mutableListOf<String>()
      val homeImportOnExit = mutableListOf<String>()
      val homeImportReceivedEvents = mutableListOf<Event>()
      val mainFlowReceivedEvents = mutableListOf<Event>()
      // homeImport consumes its own ChildFinishRequest with Stay. If it returned Ignore the
      // event would bubble up to mainFlowImport (per the existing test
      // "child-finish handler bubbles to parent flow when parallel returns Ignore"). Consuming
      // it here lets us verify that mainFlowImport does NOT see a TabA event — proving the
      // typed child-finish is scoped to the inner parallel until it explicitly bubbles.
      val homeImportNode = TestParallelNode(
        onEntryImpl = { homeImportOnEntry.add("entered") },
        onExitImpl = { homeImportOnExit.add("exited") },
        parallelTransitions = listOf(
          TestParallelTransitionSpec(
            eventMatcher = { it is HomeImportChildFinishRequest },
            transition = Stay,
          ),
        ),
        onTransitionCallback = { homeImportReceivedEvents.add(it) },
      )

      val sut = buildAcmeSandwichService(
        createMainFlowImport = {
          object : FlowNode<Unit> {
            override val initial: Target = Target.mainFlowImport.mainScreen
            override val dismissResult = Unit
            override fun transition(event: Event): FlowTransition<Unit> {
              mainFlowReceivedEvents.add(event)
              return when {
                event is TestEvent && event.name == "deeplink" ->
                  tabAScreenAbsPath?.let { NavigateTo(AbsoluteTarget(it)) } ?: Ignore

                event is TestEvent && event.name == "back" ->
                  NavigateTo(Target.mainFlowImport.mainScreen)

                else -> Ignore
              }
            }
          }
        },
        createHomeImport = { homeImportNode },
        tabARootFactory = {
          TestFlowNode(
            initialTarget = Target.tabA.tabAScreen,
            transitions = listOf(
              TestFlowTransitionSpec(
                eventMatcher = { it is TestEvent && it.name == "finishTabA" },
                transition = Finish(Unit),
              ),
            ),
          )
        },
      )

      sut.collectTransitions().test {
        val initial = awaitItem()

        // Pre-state: rootNode is the outer parallel; only two top-level regions are alive
        // (mainFlowImport + siblingSheet). homeImport is not in _intermediateParallels.
        (initial.rootNode is ParallelFlowNode<*>) shouldBe true
        initial.regions.keys.map { it.path.lastSegment().name }.toSet()
          .shouldContainOnly("mainFlowImport", "siblingSheet")
        val mainRegion = initial.regions.entries.first { it.key.path.lastSegment().name == "mainFlowImport" }
        mainRegion.value.active.lastSegment().name shouldBe "mainScreen"
        val sheetRegion = initial.regions.entries.first { it.key.path.lastSegment().name == "siblingSheet" }
        sheetRegion.value.active.lastSegment().name shouldBe "sheetScreen"
        initial._intermediateParallels.isEmpty() shouldBe true
        homeImportOnEntry.isEmpty() shouldBe true

        // Compute the absolute path to tabA.tabAScreen — the SAME schema instances the service
        // sees. Build it explicitly from rootSegment + mainImportSegment + homeImportSegment +
        // tabASegment + tabAScreenSegment.
        val outerSchema = ParallelTestAcmeSandwichSchema(
          mainFlowImportSchema = ParallelTestAcmeSandwichMainSchema(
            homeImportSchema = ParallelTestAcmeSandwichHomeSchema(
              tabASchema = ParallelTestAcmeSandwichTabASchema(),
              tabBSchema = ParallelTestAcmeSandwichTabBSchema(),
            ),
          ),
          siblingSheetSchema = ParallelTestAcmeSandwichSheetSchema(
            installationImportSchema = ParallelTestAcmeSandwichInstallSchema(),
          ),
        )
        val mainSchema = ParallelTestAcmeSandwichMainSchema(
          homeImportSchema = ParallelTestAcmeSandwichHomeSchema(
            tabASchema = ParallelTestAcmeSandwichTabASchema(),
            tabBSchema = ParallelTestAcmeSandwichTabBSchema(),
          ),
        )
        val homeSchema = ParallelTestAcmeSandwichHomeSchema(
          tabASchema = ParallelTestAcmeSandwichTabASchema(),
          tabBSchema = ParallelTestAcmeSandwichTabBSchema(),
        )
        val rootSegment = outerSchema.rootSegment
        val mainImportSegment = outerSchema.childSchemas.keys.first { it.name == "mainFlowImport" }
        val homeImportSegment = mainSchema.childSchemas.keys.first { it.name == "homeImport" }
        val tabASegment = homeSchema.childSchemas.keys.first { it.name == "tabA" }
        val tabAScreenSegment = Target.tabA.tabAScreen.path.firstSegment()
        tabAScreenAbsPath = Path(
          listOf(rootSegment, mainImportSegment, homeImportSegment, tabASegment, tabAScreenSegment),
        )

        sut.sendEvent(TestEvent("deeplink"))
        val afterMount = awaitItem()

        // Intermediate parallel mounted at runtime; its onEntry fired exactly once; the
        // intermediate path lives in `_intermediateParallels`.
        afterMount._intermediateParallels.keys.map { it.toString() }.toSet().shouldContainOnly(
          Path(listOf(rootSegment, mainImportSegment, homeImportSegment)).toString(),
        )
        homeImportOnEntry.size shouldBe 1

        // tabA + tabB regions are alive at their default initial screens; siblingSheet is
        // untouched. mainFlowImport region is also still alive (its active path now reaches
        // through to tabAScreen via the explicit deep link).
        afterMount.regions.keys.map { it.path.lastSegment().name }.toSet().shouldContainOnly(
          "mainFlowImport",
          "siblingSheet",
          "tabA",
          "tabB",
        )
        val tabARegion = afterMount.regions.entries.first { it.key.path.lastSegment().name == "tabA" }
        tabARegion.value.active.lastSegment().name shouldBe "tabAScreen"
        val tabBRegion = afterMount.regions.entries.first { it.key.path.lastSegment().name == "tabB" }
        tabBRegion.value.active.lastSegment().name shouldBe "tabBScreen"

        // Drive a Finish from inside tabA. The runtime emits a
        // HomeImportChildFinishRequest.TabA into the inner parallel's transition().
        homeImportReceivedEvents.clear()
        val mainFlowEventCountBefore = mainFlowReceivedEvents.size
        sut.sendEvent(TestEvent("finishTabA"))
        awaitItem() // tabA flow returns Finish → emits RootFinishRequestEvent
        awaitItem() // RootFinishRequestEvent → emits EnqueueEvent(HomeImportChildFinishRequest.TabA)
        awaitItem() // typed child-finish event delivered to homeImport.transition (Stay)

        homeImportReceivedEvents.any { it is HomeImportChildFinishRequest.TabA } shouldBe true
        // mainFlowImport did NOT receive a HomeImportChildFinishRequest because homeImport
        // consumes it with Stay above. If homeImport returned Ignore the event would bubble
        // to mainFlow per the existing "child-finish handler bubbles to parent flow when
        // parallel returns Ignore" contract.
        mainFlowReceivedEvents.drop(mainFlowEventCountBefore)
          .none { it is HomeImportChildFinishRequest } shouldBe true

        // Continue the same scenario into the unmount phase: back navigation from a
        // post-Finish-Stay state. The Stay drained-but-no-targetPaths cycle previously
        // tripped the buggy "neededIntermediates derived from targetPaths only" derivation
        // and unmounted homeImport prematurely (extra onExit). With the post-alive-update
        // unmount rule + initMounted flag, the intermediate stays through the Stay cycle
        // and unmounts exactly once on the back-nav.
        sut.sendEvent(TestEvent("back"))
        val afterBack = awaitItem()
        afterBack._intermediateParallels.isEmpty() shouldBe true
        // Exactly one onExit total across Init → deeplink → finishTabA-Stay → back.
        homeImportOnExit.size shouldBe 1
        afterBack.regions.keys.map { it.path.lastSegment().name }.toSet()
          .shouldContainOnly("mainFlowImport", "siblingSheet")
        afterBack.regions.entries.first { it.key.path.lastSegment().name == "mainFlowImport" }
          .value.active.lastSegment().name shouldBe "mainScreen"

        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── start(payload) → parallel root onEntry ────────────────────────────────
    // A real-world app cold-starts via `navigationService.start(deeplink)` where the schema root is a parallel
    // (AppFlowNode is `ParallelFlowNode<Unit>`). materializeRegion's parallel-root branch passes
    // `event.payload` (NavigationService.kt:189-193 — the rootSegmentPath payload map). No
    // existing test asserts that the payload actually reaches the parallel root's onEntry.
    should("start(payload) for a parallel-rooted schema delivers the payload to the root parallel's onEntry") {
      val captured = mutableListOf<Event>()
      val capturingRoot = object : ParallelFlowNode<Unit>() {
        override val dismissResult = Unit
        override fun transition(event: Event): FlowTransition<Unit> = Ignore
        override fun onEntry(event: Event) {
          super.onEntry(event)
          captured.add(event)
        }
      }
      val payload = "acme-deeplink-payload"
      val sut = buildTopRootServiceWithCustomRoot(createRootParallel = { capturingRoot })

      sut.start(payload)

      captured.size shouldBe 1
      val seen = captured.first()
      (seen is InitEvent) shouldBe true
      (seen as InitEvent).payload shouldBe payload
    }

    // ── G-A/G-B (real-project coverage): parameterized parallelFlow root + parameterized ─────────
    // intermediate flow importing a parallel. Mirrors a real-world app's cold-start EXACTLY:
    //   appFlow [parallelFlow, param=initialDeeplink] -> mainFlow [param=initialDeeplink] + sheetFlow
    //   mainFlow -> mainScreen + homeFlow [parallelFlow] -> { tabA, tabB }
    // No prior parallel fixture carried a parameterName, so this is the first coverage of the
    // parameterized-parallel codegen path (Factory.createRootNode(param): ParallelFlowNode<*>) AND of
    // start(payload) routing the SAME payload to both the parallel root and the parameterized
    // sub-region root (NavigationService.materializeRegion seeds mapOf(regionRootPath to payload) —
    // this is the "Way simultaneously routes it to mainFlow sub-region" contract AppFlowNode relies on).
    should(
      "acme cold-start: start(deeplink) on a parameterized parallelFlow root delivers the SAME " +
        "payload to the root parallel AND the parameterized intermediate sub-region root, then a " +
        "NavigateTo mounts the imported inner parallel",
    ) {
      val rootReceived = mutableListOf<String>()
      val mainReceived = mutableListOf<String>()
      val payload = "deeplink://acme/activate"
      var tabAScreenAbsPath: Path? = null

      val sut = buildParamswService(
        createParamAppRoot = { deeplink ->
          rootReceived.add(deeplink)
          TestParallelNode()
        },
        createParamMainImport = { deeplink ->
          mainReceived.add(deeplink)
          object : FlowNode<Unit> {
            override val initial: Target = Target.paramMainImport.paramMainScreen
            override val dismissResult = Unit
            override fun transition(event: Event): FlowTransition<Unit> = when {
              event is TestEvent && event.name == "deeplink" ->
                tabAScreenAbsPath?.let { NavigateTo(AbsoluteTarget(it)) } ?: Ignore

              else -> Ignore
            }
          }
        },
      )

      sut.collectTransitions(rootNodePayload = payload).test {
        val initial = awaitItem()

        // G-A: the parameterized parallel root received the deeplink exactly once...
        rootReceived shouldBe listOf(payload)
        // ...and Way ALSO routed the SAME payload to the parameterized intermediate sub-region root.
        mainReceived shouldBe listOf(payload)

        // Only the two top-level regions are alive; the imported inner parallel (paramHomeImport) is
        // NOT pre-mounted because paramMainImport's initial child is paramMainScreen (a screen).
        (initial.rootNode is ParallelFlowNode<*>) shouldBe true
        initial.regions.keys.map { it.path.lastSegment().name }.toSet()
          .shouldContainOnly("paramMainImport", "paramSheetImport")
        initial._intermediateParallels.isEmpty() shouldBe true

        // Build the absolute path to paramTabA.paramTabAScreen through the parameterized parents.
        val homeSchema = ParallelTestParamswHomeSchema(
          paramTabASchema = ParallelTestParamswTabASchema(),
          paramTabBSchema = ParallelTestParamswTabBSchema(),
        )
        val outerSchema = ParallelTestParamswSchema(
          paramMainImportSchema = ParallelTestParamswMainSchema(paramHomeImportSchema = homeSchema),
          paramSheetImportSchema = ParallelTestParamswSheetSchema(),
        )
        val mainSchema = ParallelTestParamswMainSchema(paramHomeImportSchema = homeSchema)
        val rootSegment = outerSchema.rootSegment
        val mainImportSegment = outerSchema.childSchemas.keys.first { it.name == "paramMainImport" }
        val homeImportSegment = mainSchema.childSchemas.keys.first { it.name == "paramHomeImport" }
        val tabASegment = homeSchema.childSchemas.keys.first { it.name == "paramTabA" }
        val tabAScreenSegment = Target.paramTabA.paramTabAScreen.path.firstSegment()
        tabAScreenAbsPath = Path(
          listOf(rootSegment, mainImportSegment, homeImportSegment, tabASegment, tabAScreenSegment),
        )

        // G-B: NavigateTo into the imported inner parallel mounts the intermediate parallel and
        // materialises both tabs — proving parameters and intermediate-parallel mounting coexist.
        sut.sendEvent(TestEvent("deeplink"))
        val afterMount = awaitItem()

        afterMount._intermediateParallels.keys.map { it.toString() }.toSet().shouldContainOnly(
          Path(listOf(rootSegment, mainImportSegment, homeImportSegment)).toString(),
        )
        afterMount.regions.keys.map { it.path.lastSegment().name }.toSet().shouldContainOnly(
          "paramMainImport",
          "paramSheetImport",
          "paramTabA",
          "paramTabB",
        )
        afterMount.regions.entries.first { it.key.path.lastSegment().name == "paramTabA" }
          .value.active.lastSegment().name shouldBe "paramTabAScreen"

        // Each parameterized root consumed its payload exactly once — no duplicate deliveries during
        // the mount transition (the Init payload is not persisted, and neither root is rebuilt).
        rootReceived shouldBe listOf(payload)
        mainReceived shouldBe listOf(payload)

        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── G-G (real-project coverage): screen sibling to an imported schema inside a parallel ───────
    // sub-region. Mirrors a real-world app's sheetFlow: placeholder [screen] + installationFlow [schema, result].
    // Navigating sheetScreen -> installationImport replaces the screen on the alive stack; the
    // imported schema's typed Finish bubbles as SiblingSheetChildFinishRequest.InstallationImport(
    // result) to siblingSheet, which navigates back to sheetScreen — WITHOUT the finish reaching the
    // outer parallel root and WITHOUT disturbing the mainFlowImport sibling region.
    should(
      "sibling screen + imported schema: NavigateTo schema replaces the screen, typed child-finish " +
        "returns to the screen and does not bubble to the parallel root",
    ) {
      val outerRootEvents = mutableListOf<Event>()
      val sheetFinishResults = mutableListOf<String>()

      val sut = buildAcmeSandwichService(
        createOuterRoot = { TestParallelNode(onTransitionCallback = { outerRootEvents.add(it) }) },
        createSiblingSheet = {
          object : FlowNode<Unit> {
            override val initial: Target = Target.siblingSheet.sheetScreen
            override val dismissResult = Unit
            override fun transition(event: Event): FlowTransition<Unit> = when {
              event is TestEvent && event.name == "openInstall" ->
                NavigateTo(Target.siblingSheet.installationImport)

              event is SiblingSheetChildFinishRequest.InstallationImport -> {
                sheetFinishResults.add(event.result)
                NavigateTo(Target.siblingSheet.sheetScreen)
              }

              else -> Ignore
            }
          }
        },
        createInstallationImport = {
          object : FlowNode<String> {
            override val initial: Target = Target.installationImport.installScreen
            override val dismissResult = "installed"
            override fun transition(event: Event): FlowTransition<String> = when {
              event is TestEvent && event.name == "finishInstall" -> Finish("installed")
              else -> Ignore
            }
          }
        },
      )

      sut.collectTransitions().test {
        val sheetActive = { s: NavigationState ->
          s.regions.entries.first { it.key.path.lastSegment().name == "siblingSheet" }
            .value.active.lastSegment().name
        }
        val initial = awaitItem()
        sheetActive(initial) shouldBe "sheetScreen"

        // sheetScreen -> installationImport: the imported schema replaces the placeholder screen.
        sut.sendEvent(TestEvent("openInstall"))
        val afterOpen = awaitItem()
        sheetActive(afterOpen) shouldBe "installScreen"
        // The mainFlowImport sibling region is untouched by sheet navigation.
        afterOpen.regions.entries.first { it.key.path.lastSegment().name == "mainFlowImport" }
          .value.active.lastSegment().name shouldBe "mainScreen"

        // installationImport Finish -> typed child-finish to siblingSheet -> navigate back to screen.
        sut.sendEvent(TestEvent("finishInstall"))
        var afterFinish = awaitItem()
        while (sheetActive(afterFinish) != "sheetScreen") {
          afterFinish = awaitItem()
        }

        // siblingSheet consumed the typed finish (with its result) and returned to the placeholder.
        sheetFinishResults shouldBe listOf("installed")
        // Because siblingSheet consumed the child-finish, it never bubbled to the outer parallel root
        // as OuterRootChildFinishRequest.SiblingSheet (which is what the root WOULD see had siblingSheet
        // returned Ignore — see "child-finish handler bubbles to parent flow when parallel returns Ignore").
        outerRootEvents.none { it is OuterRootChildFinishRequest } shouldBe true

        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── G-D (real-project coverage + CRITICAL-2 regression): a ROOT parallel routing Back ─────────
    // sheet-first-else-head via its OWN app-owned foreground field. Mirrors a real-world app's AppFlowNode:
    // the app decides which sub-region Back targets and returns DispatchBackTo(foreground). Back must
    // be routed to EXACTLY ONE sub-region — NOT broadcast to every root sub-region. Before the fix in
    // TargetResolution.kt (resolveTransition's Back fold guard + root-parallel Back dispatch), a single
    // Back reached both the head and the sheet regions, defeating the routing (the real app's head reacted to a
    // back-press meant only to close the sheet).
    should(
      "DispatchBackTo from a ROOT parallel routes Back ONLY to the sheet region while it shows " +
        "content, then ONLY to the head region once the sheet is back at its placeholder",
    ) {
      val backReceivedBy = mutableListOf<String>()
      // The root parallel holds its OWN foreground field (app-side presentation) and routes Back into it.
      val rootParallel = object : ParallelFlowNode<Unit>() {
        var foreground: RegionId? = null
        override val dismissResult = Unit
        override fun transition(event: Event): FlowTransition<Unit> = if (event == Event.Back) {
          DispatchBackTo(requireNotNull(foreground) { "app must set the foreground region" })
        } else {
          Ignore
        }
      }

      val sut = buildAcmeSandwichService(
        createOuterRoot = { rootParallel },
        createMainFlowImport = {
          object : FlowNode<Unit> {
            override val initial: Target = Target.mainFlowImport.mainScreen
            override val dismissResult = Unit
            override fun transition(event: Event): FlowTransition<Unit> = when (event) {
              is BackEvent -> {
                backReceivedBy.add("head")
                Stay
              }

              else -> Ignore
            }
          }
        },
        createSiblingSheet = {
          object : FlowNode<Unit> {
            override val initial: Target = Target.siblingSheet.sheetScreen
            override val dismissResult = Unit
            override fun transition(event: Event): FlowTransition<Unit> = when {
              event is TestEvent && event.name == "openInstall" ->
                NavigateTo(Target.siblingSheet.installationImport)

              event is SiblingSheetChildFinishRequest.InstallationImport ->
                NavigateTo(Target.siblingSheet.sheetScreen)

              else -> Ignore
            }
          }
        },
        createInstallationImport = {
          object : FlowNode<String> {
            override val initial: Target = Target.installationImport.installScreen
            override val dismissResult = "closed"
            override fun transition(event: Event): FlowTransition<String> = when (event) {
              // Back while the sheet shows content closes the sheet (the real app's ModalBottomSheet).
              is BackEvent -> {
                backReceivedBy.add("sheet")
                Finish("closed")
              }

              else -> Ignore
            }
          }
        },
      )

      sut.collectTransitions().test {
        val sheetActive = { s: NavigationState ->
          s.regions.entries.first { it.key.path.lastSegment().name == "siblingSheet" }
            .value.active.lastSegment().name
        }
        val initial = awaitItem() // initial: sheet at sheetScreen (placeholder)
        val headRegionId = initial.regions.keys.first { it.path.lastSegment().name == "mainFlowImport" }
        val sheetRegionId = initial.regions.keys.first { it.path.lastSegment().name == "siblingSheet" }

        // App opens the sheet → the app marks the sheet as its foreground.
        sut.sendEvent(TestEvent("openInstall"))
        awaitItem() // sheet -> installScreen (content visible)
        rootParallel.foreground = sheetRegionId

        // Foreground = sheet → Back routes ONLY into the sheet region, which closes it and returns to
        // the placeholder. The head region must NOT receive this Back.
        sut.sendEvent(Event.Back)
        var afterFirstBack = awaitItem()
        while (sheetActive(afterFirstBack) != "sheetScreen") {
          afterFirstBack = awaitItem()
        }
        backReceivedBy shouldBe listOf("sheet")

        // Sheet closed → the app marks the head as its foreground; Back now routes ONLY to head.
        rootParallel.foreground = headRegionId
        sut.sendEvent(Event.Back)
        awaitItem()
        backReceivedBy shouldBe listOf("sheet", "head")

        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── NEW: transition(Event.Back) is the single point of parallel Back control ──────────────────
    // These lock the four outcomes a parallel's transition(Event.Back) can produce, plus the
    // stale-id soft-fallback, at both root and nested levels.

    should("transition(Event.Back)=Stay swallows Back at a root parallel (no finish, no crash)") {
      var onFinishCalled = false
      val root = object : ParallelFlowNode<Unit>() {
        override val dismissResult = Unit
        override fun transition(event: Event): FlowTransition<Unit> = if (event == Event.Back) Stay else Ignore
      }
      val sut = buildTopRootServiceWithCustomRoot(
        createRootParallel = { root },
        onFinishRequest = {
          onFinishCalled = true
          Ignore
        },
      )
      sut.collectTransitions().test {
        awaitItem() // initial
        // Back is swallowed by the root parallel's Stay — it must NOT finish the parallel.
        sut.sendEvent(Event.Back)
        onFinishCalled shouldBe false
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("transition(Event.Back)=Finish finishes a root parallel (onFinishRequest invoked)") {
      var onFinishCalled = false
      val root = object : ParallelFlowNode<Unit>() {
        override val dismissResult = Unit
        override fun transition(event: Event): FlowTransition<Unit> = if (event == Event.Back) Finish(Unit) else Ignore
      }
      val sut = buildTopRootServiceWithCustomRoot(
        createRootParallel = { root },
        onFinishRequest = {
          onFinishCalled = true
          Ignore
        },
      )
      sut.collectTransitions().test {
        awaitItem() // initial
        sut.sendEvent(Event.Back)
        cancelAndIgnoreRemainingEvents()
      }
      // Finish from transition(Back) bubbles to the service's onFinishRequest.
      onFinishCalled shouldBe true
    }

    should("transition(Event.Back)=Finish finishes a flow-nested parallel (bubbles to the enclosing flow)") {
      // par01Main is a parallel that is the body of the par01App root flow (NOT a root parallel).
      // Finish from its transition(Back) must bubble as the flow's finish — here to the root flow,
      // so the service's onFinishRequest is invoked. Confirms flow-nested parallel Finish-on-Back
      // is wired, not silently dropped.
      var onFinishCalled = false
      val sut = buildPar01Service(
        parallelTransitions = listOf(trp<BackEvent>(Finish(Unit))),
        onFinishRequest = {
          onFinishCalled = true
          Ignore
        },
      )
      sut.collectTransitions().test {
        awaitItem()
        sut.sendEvent(Event.Back)
        cancelAndIgnoreRemainingEvents()
      }
      onFinishCalled shouldBe true
    }

    should("flow-nested parallel Finish on the dispatchBackThroughParallel re-consultation still finishes") {
      // Hardening for the re-consultation path: on Back a flow-nested parallel's transition(Back) is
      // consulted twice — once in the region fold, once inside dispatchBackThroughParallel. If the
      // first returns Ignore (bubbles to maybeResolveBackEvent) and the re-consultation returns Finish,
      // dispatchBackThroughParallel's else branch must route it through resolveTransitionInRegion (the
      // schema-based finish), NOT drop it via a null finishTransitionBuilder. Drives exactly that
      // sequence and asserts the root flow's onFinishRequest fires.
      var onFinishCalled = false
      val sut = buildPar01Service(
        mainBackTransitionQueue = mutableListOf(Ignore, Finish(Unit)),
        onFinishRequest = {
          onFinishCalled = true
          Ignore
        },
      )
      sut.collectTransitions().test {
        awaitItem()
        sut.sendEvent(Event.Back)
        cancelAndIgnoreRemainingEvents()
      }
      onFinishCalled shouldBe true
    }

    should("transition(Event.Back)=Ignore routes Back into the deepest active sub-region") {
      // Default TestParallelNode returns Ignore for Back. par01 top & bottom are equal-depth, so the
      // alphabetical tie-break in deepestRegion picks par01Top; Back there finishes the top flow and
      // the parallel observes Par01MainChildFinishRequest.Par01Top — proving Back went to top.
      val receivedEvents = mutableListOf<Event>()
      val sut = buildPar01Service(
        parallelTransitions = listOf(trp<Par01MainChildFinishRequest.Par01Top>(Stay)),
        onParallelTransition = { receivedEvents.add(it) },
      )
      sut.collectTransitions().test {
        awaitItem()
        sut.sendEvent(Event.Back)
        awaitItem() // Back → deepest(top) → Finish → EnqueueEvent(Par01Top)
        awaitItem() // child-finish drained → parallel Stay
        cancelAndIgnoreRemainingEvents()
      }
      receivedEvents.any { it is Par01MainChildFinishRequest.Par01Top } shouldBe true
    }

    should("transition(Event.Back)=DispatchBackTo(stale id) soft-falls-back to the deepest sub-region (never throws)") {
      // A DispatchBackTo carrying a region id that matches no alive sub-region must NOT crash Back;
      // it soft-falls-back to the deepest (par01Top), which finishes and yields the Par01Top child-finish.
      val receivedEvents = mutableListOf<Event>()
      val sut = buildPar01Service(
        parallelTransitions = listOf(
          trp<Par01MainChildFinishRequest.Par01Top>(Stay),
          trp<BackEvent>(DispatchBackTo(RegionId(Path(Segment("definitelyNotARealRegion"))))),
        ),
        onParallelTransition = { receivedEvents.add(it) },
      )
      sut.collectTransitions().test {
        awaitItem()
        sut.sendEvent(Event.Back) // must not throw
        awaitItem()
        awaitItem()
        cancelAndIgnoreRemainingEvents()
      }
      receivedEvents.any { it is Par01MainChildFinishRequest.Par01Top } shouldBe true
    }

    // ── CRITICAL-1 regression: cleanDispose() must fire onDispose() on a parallel-flow-ROOTED ─────
    // schema's root ParallelFlowNode. That node lives in state.rootNode — not in any region's _nodes
    // map nor in _intermediateParallels (mountIntermediateParallel skips parallelPath == rootNodePath)
    // — so before the fix cleanDispose() walked regions + intermediates and skipped it entirely,
    // leaking any coroutine scope / DI component it released in onDispose(). The real app's AppFlowNode is
    // exactly such a root ParallelFlowNode.
    should("cleanDispose() calls onDispose() on the root ParallelFlowNode of a parallel-rooted schema") {
      val disposed = mutableListOf<String>()
      val sut = buildTopRootServiceWithCustomRoot(
        createRootParallel = { TestParallelNode(onDisposeImpl = { disposed.add("topRoot") }) },
      )
      sut.start()
      disposed.isEmpty() shouldBe true

      sut.cleanDispose()

      // The root parallel's onDispose fired exactly once (before the fix it never fired at all).
      disposed shouldBe listOf("topRoot")
    }

    // ── R8 (bug-hunt re-trace): NavigateTo(AbsoluteTarget) targeting a parallel path ─────────────
    // calculateAliveNodes' `getOrPut` would create an orphan Region at the parallel's path.
    // synchronizeNodes' per-region build loop reuses an entry from `_intermediateParallels` if
    // present, but `state.rootNode` lives in a separate slot — so a fresh ParallelFlowNode would
    // get built and stored in `region._nodes[rootNodePath]`, leaving `state.rootNode` and the
    // region copy as two different instances of the same parallel (silent state desync). Reject
    // the misuse at NavigateTo resolution time with a clear actionable error.
    should(
      "NavigateTo(AbsoluteTarget) whose target path equals a ParallelFlowNode's own path throws " +
        "with a message naming the path and recommending sub-region target",
    ) {
      val rootSegment = ParallelTestTopRootSchema().rootSegment
      val rootParallelPath = Path(listOf(rootSegment))
      val alphaScreenRoot = TestFlowNode(
        initialTarget = Target.alpha.alphaScreen,
        transitions = listOf(
          TestFlowTransitionSpec(
            eventMatcher = { it is TestEvent && it.name == "navigateToParallelRoot" },
            transition = NavigateTo(AbsoluteTarget(rootParallelPath)),
          ),
        ),
      )
      val sut = run {
        val alphaNodeBuilder = AlphaNodeBuilder(
          nodeFactory = object : AlphaNodeBuilder.Factory {
            override fun createRootNode(): FlowNode<*> = alphaScreenRoot
            override fun createAlphaScreenNode(): ScreenNode = TestScreenNode()
          },
          schema = AlphaSchema(),
        )
        val betaNodeBuilder = BetaNodeBuilder(
          nodeFactory = object : BetaNodeBuilder.Factory {
            override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.beta.betaScreen)
            override fun createBetaScreenNode(): ScreenNode = TestScreenNode()
          },
          schema = BetaSchema(),
        )
        val topRootNodeBuilder = TopRootNodeBuilder(
          nodeFactory = object : TopRootNodeBuilder.Factory {
            override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
            override fun createAlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
            override fun createBetaNodeBuilder(): NodeBuilder = betaNodeBuilder
          },
          schema = ParallelTestTopRootSchema(),
        )
        NavigationService<Unit>(nodeBuilder = topRootNodeBuilder, onFinishRequest = { Ignore })
      }

      var thrown: Throwable? = null
      sut.collectTransitions().test {
        awaitItem() // initial
        thrown = runCatching { sut.sendEvent(TestEvent("navigateToParallelRoot")) }.exceptionOrNull()
        cancelAndIgnoreRemainingEvents()
      }

      (thrown is IllegalArgumentException) shouldBe true
      val message = thrown?.message ?: ""
      message.contains(rootParallelPath.toString()) shouldBe true
      message.contains("ParallelFlowNode") shouldBe true
      message.contains("sub-region") shouldBe true
    }

    // ── R9 (bug-hunt re-trace) whole-region prune variant ────────────────────────────────────────
    // Companion to the per-region prune test in NavigationServiceTest. synchronizeNodes' B1 loop
    // (line ~576-586) handles regions that no longer exist in state._regions — fully-pruned
    // sub-regions of an unmounted intermediate parallel hit this path. acme-realistic shape:
    // outerApp [flow] → outerScreen + importedParallel [parallelFlow] → leftTab + rightTab.
    // Navigating from leftScreen back to outerScreen unmounts the intermediate AND fully
    // prunes leftTab + rightTab regions. If one tab's screen onExit throws, the OTHER tab's
    // screen + flow-root onExit calls must still fire (DI scope tear-down via
    // CoroutineScopeHooks depends on every node getting a chance).
    should(
      "R9 whole-region prune: every alive node in a fully-pruned region receives onExit even when one throws",
    ) {
      val exitedPaths = mutableListOf<String>()
      var leftScreenAbsPath: Path? = null
      val sut = buildLazyIntermediateService(
        createOuterApp = {
          object : FlowNode<Unit> {
            override val initial: Target = Target.outerApp.outerScreen
            override val dismissResult = Unit
            override fun transition(event: Event): FlowTransition<Unit> = when {
              event is TestEvent && event.name == "deeplink" ->
                leftScreenAbsPath?.let { NavigateTo(AbsoluteTarget(it)) } ?: Ignore

              event is TestEvent && event.name == "back" ->
                NavigateTo(Target.outerApp.outerScreen)

              else -> Ignore
            }
          }
        },
        createLeftScreen = {
          TestScreenNode(
            onExitImpl = {
              exitedPaths.add("leftScreen")
              error("leftScreen onExit throws")
            },
          )
        },
        createRightScreen = {
          TestScreenNode(onExitImpl = { exitedPaths.add("rightScreen") })
        },
        createLeftFlowRoot = {
          TestFlowNode(
            initialTarget = Target.leftTab.leftScreen,
            onExitImpl = { exitedPaths.add("leftFlow") },
          )
        },
        createRightFlowRoot = {
          TestFlowNode(
            initialTarget = Target.rightTab.rightScreen,
            onExitImpl = { exitedPaths.add("rightFlow") },
          )
        },
      )

      var thrown: Throwable? = null
      sut.collectTransitions().test {
        awaitItem() // initial: outerApp + outerScreen alive only
        val outerSchema = ParallelTestLazyIntermediateSchema(
          importedParallelSchema = ParallelTestLazyIntermediateInnerSchema(),
        )
        val innerSchema = ParallelTestLazyIntermediateInnerSchema()
        val outerRootSeg = outerSchema.rootSegment
        val importedSeg = outerSchema.childSchemas.keys.first { it.name == "importedParallel" }
        val leftTabSeg = innerSchema.childSchemas.keys.first { it.name == "leftTab" }
        val leftScreenSeg = Target.leftTab.leftScreen.path.firstSegment()
        leftScreenAbsPath = Path(listOf(outerRootSeg, importedSeg, leftTabSeg, leftScreenSeg))

        sut.sendEvent(TestEvent("deeplink"))
        awaitItem() // intermediate mounted + tab regions alive

        thrown = runCatching { sut.sendEvent(TestEvent("back")) }.exceptionOrNull()
        cancelAndIgnoreRemainingEvents()
      }

      // Both pruned regions' nodes received onExit even though leftScreen threw. Without R9
      // the iteration would have stopped before rightScreen / rightFlow / leftFlow had a chance
      // to fire their onExit hooks (depending on map iteration order). All four must be present
      // — the order BETWEEN regions is map-iteration-order-dependent and not asserted.
      exitedPaths.contains("leftScreen") shouldBe true
      exitedPaths.contains("rightScreen") shouldBe true
      exitedPaths.contains("leftFlow") shouldBe true
      exitedPaths.contains("rightFlow") shouldBe true
      (thrown is IllegalStateException) shouldBe true
      (thrown?.message?.contains("leftScreen onExit throws") == true) shouldBe true
    }
  }
}

// Outer flow-rooted schema (parallel-test-lazy-intermediate.dot) imports a parallel-rooted schema
// (parallel-test-lazy-intermediate-inner.dot). The intermediate parallel at
// `outerApp.importedParallel` is NOT pre-mounted at Init because the outer flow's initial child is
// `outerScreen` — Init never walks through the intermediate. NavigateTo at runtime forces the
// new runtime mount path in NavigationService.transition to materialize the intermediate.
private fun buildLazyIntermediateService(
  createOuterApp: () -> FlowNode<*> = { TestFlowNode(initialTarget = Target.outerApp.outerScreen) },
  createImportedParallel: () -> ParallelFlowNode<*> = { TestParallelNode() },
  leftTabRootFactory: () -> FlowNode<*> = { TestFlowNode(initialTarget = Target.leftTab.leftScreen) },
  rightTabRootFactory: () -> FlowNode<*> = { TestFlowNode(initialTarget = Target.rightTab.rightScreen) },
  createLeftFlowRoot: (() -> FlowNode<*>)? = null,
  createRightFlowRoot: (() -> FlowNode<*>)? = null,
  createLeftScreen: () -> ScreenNode = { TestScreenNode() },
  createRightScreen: () -> ScreenNode = { TestScreenNode() },
): NavigationService<Unit> {
  val leftTabNodeBuilder = LeftTabNodeBuilder(
    nodeFactory = object : LeftTabNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = (createLeftFlowRoot ?: leftTabRootFactory)()
      override fun createLeftScreenNode(): ScreenNode = createLeftScreen()
    },
    schema = LeftTabSchema(),
  )
  val rightTabNodeBuilder = RightTabNodeBuilder(
    nodeFactory = object : RightTabNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = (createRightFlowRoot ?: rightTabRootFactory)()
      override fun createRightScreenNode(): ScreenNode = createRightScreen()
    },
    schema = RightTabSchema(),
  )
  val innerSchema = ParallelTestLazyIntermediateInnerSchema()
  val importedParallelBuilder = ImportedParallelNodeBuilder(
    nodeFactory = object : ImportedParallelNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<*> = createImportedParallel()
      override fun createLeftTabNodeBuilder(): NodeBuilder = leftTabNodeBuilder
      override fun createRightTabNodeBuilder(): NodeBuilder = rightTabNodeBuilder
    },
    schema = innerSchema,
  )
  val outerAppNodeBuilder = OuterAppNodeBuilder(
    nodeFactory = object : OuterAppNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = createOuterApp()
      override fun createImportedParallelNodeBuilder(): NodeBuilder = importedParallelBuilder
      override fun createOuterScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParallelTestLazyIntermediateSchema(importedParallelSchema = innerSchema),
  )
  return NavigationService(nodeBuilder = outerAppNodeBuilder, onFinishRequest = { Ignore })
}

// Test-only accessor mirroring the pattern in SnapshotRollbackTest.kt — captures the current
// NavigationState via the transition listener trick so the test can introspect `_regions` and
// `_intermediateParallels` after a failed sendEvent.
private fun NavigationService<*>.snapshotForTest(): NavigationState {
  var captured: NavigationState? = null
  val listener: (NavigationState) -> Unit = { captured = it }
  this.addTransitionListener(listener)
  this.removeTransitionListener(listener)
  return captured ?: NavigationState(
    _regions = mutableMapOf(),
    _nodeExtensionPoints = mutableListOf(),
    _enqueuedEvents = ArrayDeque(),
  )
}

// Outer parallel-rooted schema wraps the inner parallel-rooted schema as its single sub-region.
// See `parallel-test-nested-root.dot` + `parallel-test-nested-inner.dot` for the topology.
private fun buildNestedRootService(
  createOuterRoot: () -> ParallelFlowNode<*> = { TestParallelNode() },
  createInnerRoot: () -> ParallelFlowNode<*> = { TestParallelNode() },
  nestedAlphaTransitions: List<TestFlowTransitionSpec> = emptyList(),
): NavigationService<Unit> {
  val nestedAlphaNodeBuilder = NestedAlphaNodeBuilder(
    nodeFactory = object : NestedAlphaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.nestedAlpha.nestedAlphaScreen,
        transitions = nestedAlphaTransitions,
      )
      override fun createNestedAlphaScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = NestedAlphaSchema(),
  )
  val nestedBetaNodeBuilder = NestedBetaNodeBuilder(
    nodeFactory = object : NestedBetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.nestedBeta.nestedBetaScreen)
      override fun createNestedBetaScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = NestedBetaSchema(),
  )
  val innerSchema = ParallelTestNestedInnerSchema()
  val innerNodeBuilder = NestedInnerNodeBuilder(
    nodeFactory = object : NestedInnerNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<*> = createInnerRoot()
      override fun createNestedAlphaNodeBuilder(): NodeBuilder = nestedAlphaNodeBuilder
      override fun createNestedBetaNodeBuilder(): NodeBuilder = nestedBetaNodeBuilder
    },
    schema = innerSchema,
  )
  val outerNodeBuilder = NestedOuterNodeBuilder(
    nodeFactory = object : NestedOuterNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<*> = createOuterRoot()
      override fun createNestedInnerNodeBuilder(): NodeBuilder = innerNodeBuilder
    },
    schema = ParallelTestNestedRootSchema(nestedInnerSchema = innerSchema),
  )
  return NavigationService(nodeBuilder = outerNodeBuilder, onFinishRequest = { Ignore })
}

// Top-level parallel-rooted schema whose `childFinish` sub-region has a non-Unit resultType.
// See `parallel-test-top-root-finish.dot` for the topology.
private fun buildTopRootFinishService(
  createRootParallel: () -> ParallelFlowNode<Unit> = { TestParallelNode() },
  childFinishTransitions: List<TestFlowTransitionSpec> = emptyList(),
  childOtherTransitions: List<TestFlowTransitionSpec> = emptyList(),
  onFinishRequest: (Unit) -> FlowTransition<Unit> = { Ignore },
): NavigationService<Unit> {
  val childFinishNodeBuilder = ChildFinishNodeBuilder(
    nodeFactory = object : ChildFinishNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNodeWithResult(
        initialTarget = Target.childFinish.childFinishScreen,
        dismissResult = 0,
        transitions = childFinishTransitions,
      )
      override fun createChildFinishScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ChildFinishSchema(),
  )
  val childOtherNodeBuilder = ChildOtherNodeBuilder(
    nodeFactory = object : ChildOtherNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.childOther.childOtherScreen,
        transitions = childOtherTransitions,
      )
      override fun createChildOtherScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ChildOtherSchema(),
  )
  val rootBuilder = RootParallelNodeBuilder(
    nodeFactory = object : RootParallelNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<*> = createRootParallel()
      override fun createChildFinishNodeBuilder(): NodeBuilder = childFinishNodeBuilder
      override fun createChildOtherNodeBuilder(): NodeBuilder = childOtherNodeBuilder
    },
    schema = ParallelTestTopRootFinishSchema(),
  )
  return NavigationService(nodeBuilder = rootBuilder, onFinishRequest = onFinishRequest)
}

private fun buildPar03ServiceWithCustomApp(
  createAppNode: () -> FlowNode<Unit>,
  alphaTransitions: List<TestFlowTransitionSpec> = emptyList(),
): NavigationService<Unit> {
  val appSchema = Parallel03Schema(
    par03MainSchema = Parallel03MainSchema(
      par03AlphaSchema = Parallel03AlphaSchema(),
      par03BetaSchema = Parallel03BetaSchema(),
    ),
  )
  val alphaNodeBuilder = Par03AlphaNodeBuilder(
    nodeFactory = object : Par03AlphaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par03Alpha.par03AlphaScreen,
        transitions = alphaTransitions,
      )
      override fun createPar03AlphaScreenNode(): ScreenNode = TestScreenNode()
      override fun createPar03AlphaScreen2Node(): ScreenNode = TestScreenNode()
    },
    schema = Parallel03AlphaSchema(),
  )
  val betaNodeBuilder = Par03BetaNodeBuilder(
    nodeFactory = object : Par03BetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par03Beta.par03BetaScreen)
      override fun createPar03BetaScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = Parallel03BetaSchema(),
  )
  val mainNodeBuilder = Par03MainNodeBuilder(
    nodeFactory = object : Par03MainNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
      override fun createPar03AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
      override fun createPar03BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
    },
    schema = Parallel03MainSchema(Parallel03AlphaSchema(), Parallel03BetaSchema()),
  )
  val appNodeBuilder = Par03AppNodeBuilder(
    nodeFactory = object : Par03AppNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = createAppNode()
      override fun createPar03MainNodeBuilder(): NodeBuilder = mainNodeBuilder
      override fun createPar03PageNode(): ScreenNode = TestScreenNode()
    },
    schema = appSchema,
  )
  return NavigationService(nodeBuilder = appNodeBuilder, onFinishRequest = { Ignore })
}

private fun buildPar05Service(
  alphaTransitions: List<TestFlowTransitionSpec> = emptyList(),
  parallelTransitions: List<TestParallelTransitionSpec> = emptyList(),
  onParallelTransition: ((Event) -> Unit)? = null,
): NavigationService<Unit> {
  val mainSchema = Parallel05MainSchema()
  val alphaNodeBuilder = Par05AlphaNodeBuilder(
    nodeFactory = object : Par05AlphaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par05Alpha.par05AlphaScreen1,
        transitions = alphaTransitions,
      )
      override fun createPar05AlphaScreen1Node(): ScreenNode = TestScreenNode()
      override fun createPar05AlphaScreen2Node(): ScreenNode = TestScreenNode()
    },
    schema = Par05AlphaSchema(),
  )
  val betaNodeBuilder = Par05BetaNodeBuilder(
    nodeFactory = object : Par05BetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par05Beta.par05BetaScreen)
      override fun createPar05BetaScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = Par05BetaSchema(),
  )
  val mainNodeBuilder = Par05MainNodeBuilder(
    nodeFactory = object : Par05MainNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
        parallelTransitions = parallelTransitions,
        onTransitionCallback = onParallelTransition,
      )
      override fun createPar05AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
      override fun createPar05BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
    },
    schema = mainSchema,
  )
  val appNodeBuilder = Par05AppNodeBuilder(
    nodeFactory = object : Par05AppNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par05App.par05Main)
      override fun createPar05MainNodeBuilder(): NodeBuilder = mainNodeBuilder
    },
    schema = Parallel05Schema(par05MainSchema = mainSchema),
  )
  return NavigationService(nodeBuilder = appNodeBuilder, onFinishRequest = { Ignore })
}

private fun buildPar04Service(): NavigationService<Unit> {
  val innerASchema = Parallel04InnerASchema()
  val innerBSchema = Parallel04InnerBSchema()
  val betaSchema = Parallel04BetaSchema()
  val alphaSchema = Parallel04AlphaSchema(par04InnerASchema = innerASchema, par04InnerBSchema = innerBSchema)
  val mainSchema = Parallel04MainSchema(par04AlphaSchema = alphaSchema, par04BetaSchema = betaSchema)
  val appSchema = Parallel04Schema(par04MainSchema = mainSchema)

  val innerANodeBuilder = Par04InnerANodeBuilder(
    nodeFactory = object : Par04InnerANodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par04InnerA.par04InnerAScreen2,
      )
      override fun createPar04InnerAScreen1Node(): ScreenNode = TestScreenNode()
      override fun createPar04InnerAScreen2Node(): ScreenNode = TestScreenNode()
    },
    schema = innerASchema,
  )
  val innerBNodeBuilder = Par04InnerBNodeBuilder(
    nodeFactory = object : Par04InnerBNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par04InnerB.par04InnerBScreen)
      override fun createPar04InnerBScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = innerBSchema,
  )
  val alphaNodeBuilder = Par04AlphaNodeBuilder(
    nodeFactory = object : Par04AlphaNodeBuilder.Factory {
      // Inner parallel: on Back, route into innerA (schema-local id, suffix-matched at runtime).
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
        parallelTransitions = listOf(trp<BackEvent>(DispatchBackTo(alphaSchema.par04InnerARegionId))),
      )
      override fun createPar04InnerANodeBuilder(): NodeBuilder = innerANodeBuilder
      override fun createPar04InnerBNodeBuilder(): NodeBuilder = innerBNodeBuilder
    },
    schema = alphaSchema,
  )
  val betaNodeBuilder = Par04BetaNodeBuilder(
    nodeFactory = object : Par04BetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par04Beta.par04BetaScreen)
      override fun createPar04BetaScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = betaSchema,
  )
  val mainNodeBuilder = Par04MainNodeBuilder(
    nodeFactory = object : Par04MainNodeBuilder.Factory {
      // Outer parallel: on Back, route into the alpha sub-region (which has the inner parallel active).
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
        parallelTransitions = listOf(trp<BackEvent>(DispatchBackTo(mainSchema.par04AlphaRegionId))),
      )
      override fun createPar04AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
      override fun createPar04BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
    },
    schema = mainSchema,
  )
  val appNodeBuilder = Par04AppNodeBuilder(
    nodeFactory = object : Par04AppNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par04App.par04Main)
      override fun createPar04MainNodeBuilder(): NodeBuilder = mainNodeBuilder
    },
    schema = appSchema,
  )
  return NavigationService(nodeBuilder = appNodeBuilder, onFinishRequest = { Ignore })
}

private fun buildPar01Service(
  parallelTransitions: List<TestParallelTransitionSpec> = emptyList(),
  onParallelTransition: ((Event) -> Unit)? = null,
  topOnExitImpl: () -> Unit = {},
  bottomOnExitImpl: () -> Unit = {},
  appTransitions: List<TestFlowTransitionSpec> = emptyList(),
  onFinishRequest: (Unit) -> FlowTransition<Unit> = { Ignore },
  mainBackTransitionQueue: MutableList<FlowTransition<Unit>>? = null,
): NavigationService<Unit> {
  val appSchema = Parallel01Schema(
    par01MainSchema = Parallel01MainSchema(
      par01TopSchema = Parallel01TopSchema(),
      par01BottomSchema = Parallel01BottomSchema(),
    ),
  )
  val topNodeBuilder = Par01TopNodeBuilder(
    nodeFactory = object : Par01TopNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par01Top.par01TopIntro,
        onExitImpl = topOnExitImpl,
      )

      override fun createPar01TopIntroNode(): ScreenNode = TestScreenNode()
    },
    schema = Parallel01TopSchema(),
  )
  val bottomNodeBuilder = Par01BottomNodeBuilder(
    nodeFactory = object : Par01BottomNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par01Bottom.par01BottomMain,
        onExitImpl = bottomOnExitImpl,
      )

      override fun createPar01BottomMainNode(): ScreenNode = TestScreenNode()
    },
    schema = Parallel01BottomSchema(),
  )
  val mainNodeBuilder = Par01MainNodeBuilder(
    nodeFactory = object : Par01MainNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
        parallelTransitions = parallelTransitions,
        onTransitionCallback = onParallelTransition,
        backTransitionQueue = mainBackTransitionQueue,
      )

      override fun createPar01BottomNodeBuilder(): NodeBuilder = bottomNodeBuilder

      override fun createPar01TopNodeBuilder(): NodeBuilder = topNodeBuilder
    },
    Parallel01MainSchema(Parallel01TopSchema(), Parallel01BottomSchema()),
  )
  val appNodeBuilder = Par01AppNodeBuilder(
    nodeFactory = object : Par01AppNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par01App.par01Main,
        transitions = appTransitions,
      )

      override fun createPar01MainNodeBuilder(): NodeBuilder = mainNodeBuilder
    },
    schema = appSchema,
  )
  return NavigationService(
    nodeBuilder = appNodeBuilder,
    onFinishRequest = onFinishRequest,
  )
}

private fun buildPar02ServiceWithCustomApp(
  createAppNode: () -> FlowNode<Unit>,
  alphaTransitions: List<TestFlowTransitionSpec> = emptyList(),
  betaTransitions: List<TestFlowTransitionSpec> = emptyList(),
): NavigationService<Unit> {
  val appSchema = Parallel02Schema(
    par02MainSchema = Parallel02MainSchema(
      par02AlphaSchema = Parallel02AlphaSchema(),
      par02BetaSchema = Parallel02BetaSchema(),
    ),
  )
  val alphaNodeBuilder = Par02AlphaNodeBuilder(
    nodeFactory = object : Par02AlphaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par02Alpha.par02AlphaScreen1,
        transitions = alphaTransitions,
      )
      override fun createPar02AlphaScreen1Node(): ScreenNode = TestScreenNode()
      override fun createPar02AlphaScreen2Node(): ScreenNode = TestScreenNode()
    },
    schema = Parallel02AlphaSchema(),
  )
  val betaNodeBuilder = Par02BetaNodeBuilder(
    nodeFactory = object : Par02BetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par02Beta.par02BetaScreen1,
        transitions = betaTransitions,
      )
      override fun createPar02BetaScreen1Node(): ScreenNode = TestScreenNode()
    },
    schema = Parallel02BetaSchema(),
  )
  val mainNodeBuilder = Par02MainNodeBuilder(
    nodeFactory = object : Par02MainNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
      override fun createPar02AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
      override fun createPar02BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
    },
    Parallel02MainSchema(Parallel02AlphaSchema(), Parallel02BetaSchema()),
  )
  val appNodeBuilder = Par02AppNodeBuilder(
    nodeFactory = object : Par02AppNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = createAppNode()
      override fun createPar02MainNodeBuilder(): NodeBuilder = mainNodeBuilder
    },
    schema = appSchema,
  )
  return NavigationService(
    nodeBuilder = appNodeBuilder,
    onFinishRequest = { Ignore },
  )
}

// Like `buildPar02Service` but counts every call to the main NodeBuilder's
// `createPar02AlphaNodeBuilder` / `createPar02BetaNodeBuilder` factories. Used to assert
// that the lazy NodeBuilder cache inside Par02MainNodeBuilder retains entries across the
// per-transition invalidateCache sweep — i.e. that navigating one region does NOT cause
// the sibling region's NodeBuilder to be recreated (a NodeBuilder rebuild constructs a
// fresh DI subcomponent in real callers and silently loses every scope-singleton state
// it owned).
private fun buildPar02ServiceWithCachedBuilderCounters(
  alphaTransitions: List<TestFlowTransitionSpec> = emptyList(),
  betaTransitions: List<TestFlowTransitionSpec> = emptyList(),
  onCreateAlpha: () -> Unit,
  onCreateBeta: () -> Unit,
): NavigationService<Unit> {
  val alphaNodeBuilder = Par02AlphaNodeBuilder(
    nodeFactory = object : Par02AlphaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par02Alpha.par02AlphaScreen1,
        transitions = alphaTransitions,
      )
      override fun createPar02AlphaScreen1Node(): ScreenNode = TestScreenNode()
      override fun createPar02AlphaScreen2Node(): ScreenNode = TestScreenNode()
    },
    schema = Parallel02AlphaSchema(),
  )
  val betaNodeBuilder = Par02BetaNodeBuilder(
    nodeFactory = object : Par02BetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par02Beta.par02BetaScreen1,
        transitions = betaTransitions,
      )
      override fun createPar02BetaScreen1Node(): ScreenNode = TestScreenNode()
    },
    schema = Parallel02BetaSchema(),
  )
  val mainNodeBuilder = Par02MainNodeBuilder(
    nodeFactory = object : Par02MainNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
      override fun createPar02AlphaNodeBuilder(): NodeBuilder {
        onCreateAlpha()
        return alphaNodeBuilder
      }
      override fun createPar02BetaNodeBuilder(): NodeBuilder {
        onCreateBeta()
        return betaNodeBuilder
      }
    },
    Parallel02MainSchema(Parallel02AlphaSchema(), Parallel02BetaSchema()),
  )
  val appNodeBuilder = Par02AppNodeBuilder(
    nodeFactory = object : Par02AppNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par02App.par02Main)
      override fun createPar02MainNodeBuilder(): NodeBuilder = mainNodeBuilder
    },
    schema = Parallel02Schema(
      par02MainSchema = Parallel02MainSchema(
        par02AlphaSchema = Parallel02AlphaSchema(),
        par02BetaSchema = Parallel02BetaSchema(),
      ),
    ),
  )
  return NavigationService(nodeBuilder = appNodeBuilder, onFinishRequest = { Ignore })
}

private fun buildPar02Service(
  alphaTransitions: List<TestFlowTransitionSpec> = emptyList(),
  betaTransitions: List<TestFlowTransitionSpec> = emptyList(),
  createMainNode: () -> ParallelFlowNode<Unit> = { TestParallelNode() },
  appTransitions: List<TestFlowTransitionSpec> = emptyList(),
): NavigationService<Unit> {
  val appSchema = Parallel02Schema(
    par02MainSchema = Parallel02MainSchema(
      par02AlphaSchema = Parallel02AlphaSchema(),
      par02BetaSchema = Parallel02BetaSchema(),
    ),
  )
  val alphaNodeBuilder = Par02AlphaNodeBuilder(
    nodeFactory = object : Par02AlphaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par02Alpha.par02AlphaScreen1,
        transitions = alphaTransitions,
      )

      override fun createPar02AlphaScreen1Node(): ScreenNode = TestScreenNode()
      override fun createPar02AlphaScreen2Node(): ScreenNode = TestScreenNode()
    },
    schema = Parallel02AlphaSchema(),
  )
  val betaNodeBuilder = Par02BetaNodeBuilder(
    nodeFactory = object : Par02BetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par02Beta.par02BetaScreen1,
        transitions = betaTransitions,
      )

      override fun createPar02BetaScreen1Node(): ScreenNode = TestScreenNode()
    },
    schema = Parallel02BetaSchema(),
  )
  val mainNodeBuilder = Par02MainNodeBuilder(
    nodeFactory = object : Par02MainNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = createMainNode()
      override fun createPar02AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
      override fun createPar02BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
    },
    Parallel02MainSchema(Parallel02AlphaSchema(), Parallel02BetaSchema()),
  )
  val appNodeBuilder = Par02AppNodeBuilder(
    nodeFactory = object : Par02AppNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par02App.par02Main,
        transitions = appTransitions,
      )

      override fun createPar02MainNodeBuilder(): NodeBuilder = mainNodeBuilder
    },
    schema = appSchema,
  )
  return NavigationService(
    nodeBuilder = appNodeBuilder,
    onFinishRequest = { Ignore },
  )
}

private fun buildPar03Service(
  appTransitions: List<TestFlowTransitionSpec> = emptyList(),
  onAlphaEntry: () -> Unit = {},
  onAlphaExit: () -> Unit = {},
  onBetaEntry: () -> Unit = {},
  onBetaExit: () -> Unit = {},
  onMainEntry: () -> Unit = {},
  onMainExit: () -> Unit = {},
): NavigationService<Unit> {
  val appSchema = Parallel03Schema(
    par03MainSchema = Parallel03MainSchema(
      par03AlphaSchema = Parallel03AlphaSchema(),
      par03BetaSchema = Parallel03BetaSchema(),
    ),
  )
  val alphaNodeBuilder = Par03AlphaNodeBuilder(
    nodeFactory = object : Par03AlphaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par03Alpha.par03AlphaScreen,
        onEntryImpl = onAlphaEntry,
        onExitImpl = onAlphaExit,
      )
      override fun createPar03AlphaScreenNode(): ScreenNode = TestScreenNode()
      override fun createPar03AlphaScreen2Node(): ScreenNode = TestScreenNode()
    },
    schema = Parallel03AlphaSchema(),
  )
  val betaNodeBuilder = Par03BetaNodeBuilder(
    nodeFactory = object : Par03BetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par03Beta.par03BetaScreen,
        onEntryImpl = onBetaEntry,
        onExitImpl = onBetaExit,
      )
      override fun createPar03BetaScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = Parallel03BetaSchema(),
  )
  val mainNodeBuilder = Par03MainNodeBuilder(
    nodeFactory = object : Par03MainNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
        onEntryImpl = onMainEntry,
        onExitImpl = onMainExit,
      )
      override fun createPar03AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
      override fun createPar03BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
    },
    schema = Parallel03MainSchema(Parallel03AlphaSchema(), Parallel03BetaSchema()),
  )
  val appNodeBuilder = Par03AppNodeBuilder(
    nodeFactory = object : Par03AppNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par03App.par03Main,
        transitions = appTransitions,
      )
      override fun createPar03MainNodeBuilder(): NodeBuilder = mainNodeBuilder
      override fun createPar03PageNode(): ScreenNode = TestScreenNode()
    },
    schema = appSchema,
  )
  return NavigationService(
    nodeBuilder = appNodeBuilder,
    onFinishRequest = { Ignore },
  )
}

private fun buildParfmService(
  alphaTransitions: List<TestFlowTransitionSpec> = emptyList(),
  parallelTransitions: List<TestParallelTransitionSpec> = emptyList(),
  onParallelTransition: ((Event) -> Unit)? = null,
): NavigationService<Unit> {
  val alphaSchema = ParallelFlatMixAlphaSchema()
  val mainSchema = ParallelFlatMixMainSchema(parfmAlphaSchema = alphaSchema)
  val alphaNodeBuilder = ParfmAlphaNodeBuilder(
    nodeFactory = object : ParfmAlphaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.parfmAlpha.parfmAlphaScreen1,
        transitions = alphaTransitions,
      )
      override fun createParfmAlphaScreen1Node(): ScreenNode = TestScreenNode()
      override fun createParfmAlphaScreen2Node(): ScreenNode = TestScreenNode()
    },
    schema = alphaSchema,
  )
  val betaNodeBuilder = ParfmBetaNodeBuilder(
    nodeFactory = object : ParfmBetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.parfmBeta.parfmBetaScreen)
      override fun createParfmBetaScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParfmBetaSchema(),
  )
  val mainNodeBuilder = ParfmMainNodeBuilder(
    nodeFactory = object : ParfmMainNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
        parallelTransitions = parallelTransitions,
        onTransitionCallback = onParallelTransition,
      )
      override fun createParfmAlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
      override fun createParfmBetaNodeBuilder(): NodeBuilder = betaNodeBuilder
    },
    schema = mainSchema,
  )
  val appNodeBuilder = ParfmAppNodeBuilder(
    nodeFactory = object : ParfmAppNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.parfmApp.parfmMain)
      override fun createParfmMainNodeBuilder(): NodeBuilder = mainNodeBuilder
    },
    schema = ParallelFlatMixSchema(parfmMainSchema = mainSchema),
  )
  return NavigationService(nodeBuilder = appNodeBuilder, onFinishRequest = { Ignore })
}

private fun buildPar06Service(
  innerATransitions: List<TestFlowTransitionSpec> = emptyList(),
  outerParallelTransitions: List<TestParallelTransitionSpec> = emptyList(),
  innerAlphaParallelTransitions: List<TestParallelTransitionSpec> = emptyList(),
  innerAInitial: Target = Target.par06InnerA.par06InnerAScreen1,
): NavigationService<Unit> {
  val mainSchema = Parallel06MainSchema()
  val alphaSchema = Par06AlphaSchema()
  val innerANodeBuilder = Par06InnerANodeBuilder(
    nodeFactory = object : Par06InnerANodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = innerAInitial,
        transitions = innerATransitions,
      )
      override fun createPar06InnerAScreen1Node(): ScreenNode = TestScreenNode()
      override fun createPar06InnerAScreen2Node(): ScreenNode = TestScreenNode()
    },
    schema = Par06InnerASchema(),
  )
  val innerBNodeBuilder = Par06InnerBNodeBuilder(
    nodeFactory = object : Par06InnerBNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par06InnerB.par06InnerBScreen)
      override fun createPar06InnerBScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = Par06InnerBSchema(),
  )
  val alphaNodeBuilder = Par06AlphaNodeBuilder(
    nodeFactory = object : Par06AlphaNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
        parallelTransitions = innerAlphaParallelTransitions,
      )
      override fun createPar06InnerANodeBuilder(): NodeBuilder = innerANodeBuilder
      override fun createPar06InnerBNodeBuilder(): NodeBuilder = innerBNodeBuilder
    },
    schema = alphaSchema,
  )
  val betaNodeBuilder = Par06BetaNodeBuilder(
    nodeFactory = object : Par06BetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par06Beta.par06BetaScreen)
      override fun createPar06BetaScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = Par06BetaSchema(),
  )
  val mainNodeBuilder = Par06MainNodeBuilder(
    nodeFactory = object : Par06MainNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
        parallelTransitions = outerParallelTransitions,
      )
      override fun createPar06AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
      override fun createPar06BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
    },
    schema = mainSchema,
  )
  val appNodeBuilder = Par06AppNodeBuilder(
    nodeFactory = object : Par06AppNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par06App.par06Main)
      override fun createPar06MainNodeBuilder(): NodeBuilder = mainNodeBuilder
    },
    schema = Parallel06Schema(par06MainSchema = mainSchema),
  )
  return NavigationService(nodeBuilder = appNodeBuilder, onFinishRequest = { Ignore })
}

private fun buildPar06mService(
  alphaInnerTransitions: List<TestParallelTransitionSpec> = emptyList(),
  alphaInnerTransitionCallback: ((Event) -> Unit)? = null,
  outerParallelTransitions: List<TestParallelTransitionSpec> = emptyList(),
  outerParallelTransitionCallback: ((Event) -> Unit)? = null,
): NavigationService<Unit> {
  val alphaSchema = Parallel06MixedAlphaSchema()
  val mainSchema = Parallel06MixedMainSchema(par06mAlphaSchema = alphaSchema)
  val innerANodeBuilder = Par06mInnerANodeBuilder(
    nodeFactory = object : Par06mInnerANodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par06mInnerA.par06mInnerAScreen)
      override fun createPar06mInnerAScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = Par06mInnerASchema(),
  )
  val innerBNodeBuilder = Par06mInnerBNodeBuilder(
    nodeFactory = object : Par06mInnerBNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par06mInnerB.par06mInnerBScreen)
      override fun createPar06mInnerBScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = Par06mInnerBSchema(),
  )
  val alphaNodeBuilder = Par06mAlphaNodeBuilder(
    nodeFactory = object : Par06mAlphaNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
        parallelTransitions = alphaInnerTransitions,
        onTransitionCallback = alphaInnerTransitionCallback,
      )
      override fun createPar06mInnerANodeBuilder(): NodeBuilder = innerANodeBuilder
      override fun createPar06mInnerBNodeBuilder(): NodeBuilder = innerBNodeBuilder
    },
    schema = alphaSchema,
  )
  val betaNodeBuilder = Par06mBetaNodeBuilder(
    nodeFactory = object : Par06mBetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par06mBeta.par06mBetaScreen)
      override fun createPar06mBetaScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = Par06mBetaSchema(),
  )
  val mainNodeBuilder = Par06mMainNodeBuilder(
    nodeFactory = object : Par06mMainNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
        parallelTransitions = outerParallelTransitions,
        onTransitionCallback = outerParallelTransitionCallback,
      )
      override fun createPar06mAlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
      override fun createPar06mBetaNodeBuilder(): NodeBuilder = betaNodeBuilder
    },
    schema = mainSchema,
  )
  val appNodeBuilder = Par06mAppNodeBuilder(
    nodeFactory = object : Par06mAppNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par06mApp.par06mMain)
      override fun createPar06mMainNodeBuilder(): NodeBuilder = mainNodeBuilder
    },
    schema = Parallel06MixedSchema(par06mMainSchema = mainSchema),
  )
  return NavigationService(nodeBuilder = appNodeBuilder, onFinishRequest = { Ignore })
}

// A schema whose own root is `parallelFlow` (no outer flow wrapper) — mirrors a real-world app's
// `appFlow [type=parallelFlow]` layout. See `parallel-test-top-root.dot` for the graph.
private fun buildTopRootService(): NavigationService<Unit> {
  val alphaNodeBuilder = AlphaNodeBuilder(
    nodeFactory = object : AlphaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.alpha.alphaScreen)
      override fun createAlphaScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = AlphaSchema(),
  )
  val betaNodeBuilder = BetaNodeBuilder(
    nodeFactory = object : BetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.beta.betaScreen)
      override fun createBetaScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = BetaSchema(),
  )
  val topRootNodeBuilder = TopRootNodeBuilder(
    nodeFactory = object : TopRootNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
      override fun createAlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
      override fun createBetaNodeBuilder(): NodeBuilder = betaNodeBuilder
    },
    schema = ParallelTestTopRootSchema(),
  )
  return NavigationService(nodeBuilder = topRootNodeBuilder, onFinishRequest = { Ignore })
}

// Variant of [buildTopRootService] that allows injecting a custom root parallel node — used for
// tests that need to attach `parallelTransitions` or callbacks to the root parallel itself.
private fun buildTopRootServiceWithCustomRoot(
  createRootParallel: () -> ParallelFlowNode<Unit>,
  onFinishRequest: (Unit) -> FlowTransition<Unit> = { Ignore },
): NavigationService<Unit> {
  val alphaNodeBuilder = AlphaNodeBuilder(
    nodeFactory = object : AlphaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.alpha.alphaScreen)
      override fun createAlphaScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = AlphaSchema(),
  )
  val betaNodeBuilder = BetaNodeBuilder(
    nodeFactory = object : BetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.beta.betaScreen)
      override fun createBetaScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = BetaSchema(),
  )
  val topRootNodeBuilder = TopRootNodeBuilder(
    nodeFactory = object : TopRootNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = createRootParallel()
      override fun createAlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
      override fun createBetaNodeBuilder(): NodeBuilder = betaNodeBuilder
    },
    schema = ParallelTestTopRootSchema(),
  )
  return NavigationService(nodeBuilder = topRootNodeBuilder, onFinishRequest = onFinishRequest)
}

// Last segment of the active path of the region whose root segment name is [regionName].
private fun NavigationState.acmeActiveLeaf(regionName: String): String =
  regions.entries.first { it.key.path.lastSegment().name == regionName }.value.active.lastSegment().name

// Acme-style layout: `parallel → app → parallel → tabs`. See the test block above for the tree.
private fun buildAcmeTabsService(
  homeTabTransitions: List<TestFlowTransitionSpec> = emptyList(),
  exploreTabTransitions: List<TestFlowTransitionSpec> = emptyList(),
  createTabsFlowNode: () -> ParallelFlowNode<Unit> = { TestParallelNode() },
): NavigationService<Unit> {
  val homeTabNodeBuilder = AcmeHomeTabNodeBuilder(
    nodeFactory = object : AcmeHomeTabNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.acmeHomeTab.acmeHomeScreen,
        transitions = homeTabTransitions,
      )
      override fun createAcmeHomeScreenNode(): ScreenNode = TestScreenNode()
      override fun createAcmeTopUpScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = AcmeHomeTabSchema(),
  )
  val exploreTabNodeBuilder = AcmeExploreTabNodeBuilder(
    nodeFactory = object : AcmeExploreTabNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.acmeExploreTab.acmeExploreScreen,
        transitions = exploreTabTransitions,
      )
      override fun createAcmeExploreScreenNode(): ScreenNode = TestScreenNode()
      override fun createAcmeExploreDetailScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = AcmeExploreTabSchema(),
  )
  val tabsFlowNodeBuilder = AcmeTabsFlowNodeBuilder(
    nodeFactory = object : AcmeTabsFlowNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = createTabsFlowNode()
      override fun createAcmeHomeTabNodeBuilder(): NodeBuilder = homeTabNodeBuilder
      override fun createAcmeExploreTabNodeBuilder(): NodeBuilder = exploreTabNodeBuilder
    },
    schema = AcmeTabsFlowSchema(),
  )
  // acmeMainFlow's only child is the nested parallel `acmeTabsFlow`. There is no FlowTarget
  // generated for a LOCAL parallel child of a LOCAL flow, so the initial target reaches a
  // screen inside the nested parallel — the runtime auto-initializes both tab sub-regions
  // when entering the parallel on the way to that screen.
  val mainFlowNodeBuilder = AcmeMainFlowNodeBuilder(
    nodeFactory = object : AcmeMainFlowNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.acmeHomeTab.acmeHomeScreen)
      override fun createAcmeTabsFlowNodeBuilder(): NodeBuilder = tabsFlowNodeBuilder
    },
    schema = AcmeMainFlowSchema(),
  )
  val authFlowNodeBuilder = AcmeAuthFlowNodeBuilder(
    nodeFactory = object : AcmeAuthFlowNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.acmeAuthFlow.acmeAuthScreen)
      override fun createAcmeAuthScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = AcmeAuthFlowSchema(),
  )
  val appFlowNodeBuilder = AcmeAppFlowNodeBuilder(
    nodeFactory = object : AcmeAppFlowNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
      override fun createAcmeMainFlowNodeBuilder(): NodeBuilder = mainFlowNodeBuilder
      override fun createAcmeAuthFlowNodeBuilder(): NodeBuilder = authFlowNodeBuilder

      // NodeBuilderCodegen's dfsWhile descends into LOCAL flows and registers any nested
      // LocalParallel it finds on the outer Factory too. The outer's `build` routing for
      // acmeTabsFlow is unreachable in practice (the runtime always routes to acmeTabsFlow
      // via acmeMainFlow's NodeBuilder first), but the factory contract still needs satisfying.
      override fun createAcmeTabsFlowNodeBuilder(): NodeBuilder = tabsFlowNodeBuilder
    },
    schema = ParallelTestAcmeTabsSchema(),
  )
  return NavigationService(nodeBuilder = appFlowNodeBuilder, onFinishRequest = { Ignore })
}

// Parallel-rooted schema (parallel-test-relfocused.dot) with TWO imported flow sub-regions
// (alpha, beta), each itself a flow with two sibling screens AND an imported parallel-rooted
// sub-schema (alphaInner / betaInner) reachable only via deeper navigation. Lets Fix #2 tests
// prove that NavigateTo(FlowTarget | ScreenTarget) returned from the root parallel routes via
// the focused sub-region's schema rather than the parallel's parent schema (whose
// `regions.first()` fallback would silently misroute to the FIRST sub-region — or, when the
// target lives in an imported sub-schema not visible to the root, throw).
private fun buildRelFocusedServiceWithCustomRoot(
  createRootParallel: () -> ParallelFlowNode<Unit>,
): NavigationService<Unit> {
  val alphaInnerNodeBuilder = ParrelfAlphaInnerNodeBuilder(
    nodeFactory = object : ParrelfAlphaInnerNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.parrelfAlphaInner.parrelfAlphaInnerScreen,
      )
      override fun createParrelfAlphaInnerScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParallelTestRelFocusedAlphaInnerSchema(),
  )
  val alphaNodeBuilder = ParrelfAlphaNodeBuilder(
    nodeFactory = object : ParrelfAlphaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.parrelfAlpha.parrelfAlphaIntro,
      )
      override fun createParrelfAlphaIntroNode(): ScreenNode = TestScreenNode()
      override fun createParrelfAlphaDetailNode(): ScreenNode = TestScreenNode()
      override fun createParrelfAlphaInnerNodeBuilder(): NodeBuilder = alphaInnerNodeBuilder
    },
    schema = ParallelTestRelFocusedAlphaSchema(parrelfAlphaInnerSchema = ParallelTestRelFocusedAlphaInnerSchema()),
  )
  val betaInnerNodeBuilder = ParrelfBetaInnerNodeBuilder(
    nodeFactory = object : ParrelfBetaInnerNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.parrelfBetaInner.parrelfBetaInnerScreen,
      )
      override fun createParrelfBetaInnerScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParallelTestRelFocusedBetaInnerSchema(),
  )
  val betaNodeBuilder = ParrelfBetaNodeBuilder(
    nodeFactory = object : ParrelfBetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.parrelfBeta.parrelfBetaIntro,
      )
      override fun createParrelfBetaIntroNode(): ScreenNode = TestScreenNode()
      override fun createParrelfBetaDetailNode(): ScreenNode = TestScreenNode()
      override fun createParrelfBetaInnerNodeBuilder(): NodeBuilder = betaInnerNodeBuilder
    },
    schema = ParallelTestRelFocusedBetaSchema(parrelfBetaInnerSchema = ParallelTestRelFocusedBetaInnerSchema()),
  )
  val rootNodeBuilder = ParrelfRootNodeBuilder(
    nodeFactory = object : ParrelfRootNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<*> = createRootParallel()
      override fun createParrelfBetaNodeBuilder(): NodeBuilder = betaNodeBuilder
      override fun createParrelfAlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
    },
    schema = ParallelTestRelFocusedSchema(
      parrelfAlphaSchema = ParallelTestRelFocusedAlphaSchema(
        parrelfAlphaInnerSchema = ParallelTestRelFocusedAlphaInnerSchema(),
      ),
      parrelfBetaSchema = ParallelTestRelFocusedBetaSchema(
        parrelfBetaInnerSchema = ParallelTestRelFocusedBetaInnerSchema(),
      ),
    ),
  )
  return NavigationService(nodeBuilder = rootNodeBuilder, onFinishRequest = { Ignore })
}

// Parallel-rooted schema (parallel-test-cross-region-intermediate.dot) with sibling flows
// parcriAlpha + parcriBeta; parcriBeta has a sibling-of-screen import to a parallel-rooted
// sub-schema (parallel-test-cross-region-intermediate-inner.dot). Beta's initial leads to
// parcriBetaIntro, so the imported parallel is NOT pre-mounted at Init — only NavigateTo
// brings it online. Used by Fix #3's cross-region test.
private fun buildCrossRegionIntermediateService(
  createRoot: () -> ParallelFlowNode<*> = { TestParallelNode() },
  createImported: () -> ParallelFlowNode<*> = { TestParallelNode() },
  alphaScreenTransitions: List<TestScreenTransitionSpec> = emptyList(),
): NavigationService<Unit> {
  val leftTabNodeBuilder = ParcriBetaLeftNodeBuilder(
    nodeFactory = object : ParcriBetaLeftNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.parcriBetaLeft.parcriBetaLeftScreen,
      )
      override fun createParcriBetaLeftScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParcriBetaLeftSchema(),
  )
  val rightTabNodeBuilder = ParcriBetaRightNodeBuilder(
    nodeFactory = object : ParcriBetaRightNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.parcriBetaRight.parcriBetaRightScreen,
      )
      override fun createParcriBetaRightScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParcriBetaRightSchema(),
  )
  val innerSchema = ParallelTestCrossRegionIntermediateInnerSchema()
  val importedNodeBuilder = ParcriBetaImportedNodeBuilder(
    nodeFactory = object : ParcriBetaImportedNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<*> = createImported()
      override fun createParcriBetaLeftNodeBuilder(): NodeBuilder = leftTabNodeBuilder
      override fun createParcriBetaRightNodeBuilder(): NodeBuilder = rightTabNodeBuilder
    },
    schema = innerSchema,
  )
  val betaNodeBuilder = ParcriBetaNodeBuilder(
    nodeFactory = object : ParcriBetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = ScreenTarget(
          Path(
            Segment(
              "parcriBetaIntro@ParallelTestCrossRegionIntermediate:" +
                "src/commonTest/way/parallel-test-cross-region-intermediate.dot",
            ),
          ),
        ),
      )
      override fun createParcriBetaImportedNodeBuilder(): NodeBuilder = importedNodeBuilder
      override fun createParcriBetaIntroNode(): ScreenNode = TestScreenNode()
    },
    schema = ParcriBetaSchema(innerSchema),
  )
  val alphaNodeBuilder = ParcriAlphaNodeBuilder(
    nodeFactory = object : ParcriAlphaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = ScreenTarget(
          Path(
            Segment(
              "parcriAlphaScreen@ParallelTestCrossRegionIntermediate:" +
                "src/commonTest/way/parallel-test-cross-region-intermediate.dot",
            ),
          ),
        ),
      )
      override fun createParcriAlphaScreenNode(): ScreenNode = TestScreenNode(
        transitions = alphaScreenTransitions,
      )
    },
    schema = ParcriAlphaSchema(),
  )
  val rootNodeBuilder = ParcriRootNodeBuilder(
    nodeFactory = object : ParcriRootNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<*> = createRoot()
      override fun createParcriBetaNodeBuilder(): NodeBuilder = betaNodeBuilder
      override fun createParcriBetaImportedNodeBuilder(): NodeBuilder = importedNodeBuilder
      override fun createParcriAlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
    },
    schema = ParallelTestCrossRegionIntermediateSchema(parcriBetaImportedSchema = innerSchema),
  )
  return NavigationService(nodeBuilder = rootNodeBuilder, onFinishRequest = { Ignore })
}

// acme-shape sandwich layout
//   outerRoot [parallelFlow]
//     ├── mainFlowImport [type=schema, flow] → mainScreen + homeImport [type=schema, parallelFlow]
//     │                                          ├── tabA [type=schema, flow] → tabAScreen
//     │                                          └── tabB [type=schema, flow] → tabBScreen
//     └── siblingSheet [type=schema, flow] → sheetScreen
// Each `type=schema` boundary forces its own NodeBuilder import (no LOCAL inlining), exactly
// like a real multi-module app. The intermediate `homeImport` parallel is NOT pre-mounted at Init because
// mainFlowImport's initial reaches mainScreen.
private fun buildAcmeSandwichService(
  createOuterRoot: () -> ParallelFlowNode<*> = { TestParallelNode() },
  createMainFlowImport: () -> FlowNode<*> = { TestFlowNode(initialTarget = Target.mainFlowImport.mainScreen) },
  createHomeImport: () -> ParallelFlowNode<*> = { TestParallelNode() },
  createSiblingSheet: () -> FlowNode<*> = { TestFlowNode(initialTarget = Target.siblingSheet.sheetScreen) },
  createInstallationImport: () -> FlowNode<*> = {
    TestFlowNode(initialTarget = Target.installationImport.installScreen)
  },
  tabARootFactory: () -> FlowNode<*> = { TestFlowNode(initialTarget = Target.tabA.tabAScreen) },
  tabBRootFactory: () -> FlowNode<*> = { TestFlowNode(initialTarget = Target.tabB.tabBScreen) },
): NavigationService<Unit> {
  val tabANodeBuilder = TabANodeBuilder(
    nodeFactory = object : TabANodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = tabARootFactory()
      override fun createTabAScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParallelTestAcmeSandwichTabASchema(),
  )
  val tabBNodeBuilder = TabBNodeBuilder(
    nodeFactory = object : TabBNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = tabBRootFactory()
      override fun createTabBScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParallelTestAcmeSandwichTabBSchema(),
  )
  val homeSchema = ParallelTestAcmeSandwichHomeSchema(
    tabASchema = ParallelTestAcmeSandwichTabASchema(),
    tabBSchema = ParallelTestAcmeSandwichTabBSchema(),
  )
  val homeImportNodeBuilder = HomeImportNodeBuilder(
    nodeFactory = object : HomeImportNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<*> = createHomeImport()
      override fun createTabANodeBuilder(): NodeBuilder = tabANodeBuilder
      override fun createTabBNodeBuilder(): NodeBuilder = tabBNodeBuilder
    },
    schema = homeSchema,
  )
  val mainFlowImportNodeBuilder = MainFlowImportNodeBuilder(
    nodeFactory = object : MainFlowImportNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = createMainFlowImport()
      override fun createHomeImportNodeBuilder(): NodeBuilder = homeImportNodeBuilder
      override fun createMainScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParallelTestAcmeSandwichMainSchema(homeImportSchema = homeSchema),
  )
  val installationImportNodeBuilder = InstallationImportNodeBuilder(
    nodeFactory = object : InstallationImportNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = createInstallationImport()
      override fun createInstallScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParallelTestAcmeSandwichInstallSchema(),
  )
  val siblingSheetNodeBuilder = SiblingSheetNodeBuilder(
    nodeFactory = object : SiblingSheetNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = createSiblingSheet()
      override fun createInstallationImportNodeBuilder(): NodeBuilder = installationImportNodeBuilder
      override fun createSheetScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParallelTestAcmeSandwichSheetSchema(installationImportSchema = ParallelTestAcmeSandwichInstallSchema()),
  )
  val outerRootNodeBuilder = OuterRootNodeBuilder(
    nodeFactory = object : OuterRootNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<*> = createOuterRoot()
      override fun createMainFlowImportNodeBuilder(): NodeBuilder = mainFlowImportNodeBuilder
      override fun createSiblingSheetNodeBuilder(): NodeBuilder = siblingSheetNodeBuilder
    },
    schema = ParallelTestAcmeSandwichSchema(
      mainFlowImportSchema = ParallelTestAcmeSandwichMainSchema(homeImportSchema = homeSchema),
      siblingSheetSchema = ParallelTestAcmeSandwichSheetSchema(
        installationImportSchema = ParallelTestAcmeSandwichInstallSchema(),
      ),
    ),
  )
  return NavigationService(nodeBuilder = outerRootNodeBuilder, onFinishRequest = { Ignore })
}

// Parameterized clone of the acme sandwich: parallelFlow root [param=deeplink] → parameterized
// intermediate flow [param=deeplink] → imported inner parallelFlow → { tabA, tabB }, plus an
// unparameterized sibling sheet. Backs the G-A/G-B parameterized-parallel coverage.
private fun buildParamswService(
  createParamAppRoot: (String) -> ParallelFlowNode<*> = { TestParallelNode() },
  createParamMainImport: (String) -> FlowNode<*> = {
    TestFlowNode(initialTarget = Target.paramMainImport.paramMainScreen)
  },
  createParamHomeImport: () -> ParallelFlowNode<*> = { TestParallelNode() },
  createParamSheetImport: () -> FlowNode<*> = {
    TestFlowNode(initialTarget = Target.paramSheetImport.paramSheetScreen)
  },
  paramTabARootFactory: () -> FlowNode<*> = { TestFlowNode(initialTarget = Target.paramTabA.paramTabAScreen) },
  paramTabBRootFactory: () -> FlowNode<*> = { TestFlowNode(initialTarget = Target.paramTabB.paramTabBScreen) },
): NavigationService<Unit> {
  val tabANodeBuilder = ParamTabANodeBuilder(
    nodeFactory = object : ParamTabANodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = paramTabARootFactory()
      override fun createParamTabAScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParallelTestParamswTabASchema(),
  )
  val tabBNodeBuilder = ParamTabBNodeBuilder(
    nodeFactory = object : ParamTabBNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = paramTabBRootFactory()
      override fun createParamTabBScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParallelTestParamswTabBSchema(),
  )
  val homeSchema = ParallelTestParamswHomeSchema(
    paramTabASchema = ParallelTestParamswTabASchema(),
    paramTabBSchema = ParallelTestParamswTabBSchema(),
  )
  val homeImportNodeBuilder = ParamHomeImportNodeBuilder(
    nodeFactory = object : ParamHomeImportNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<*> = createParamHomeImport()
      override fun createParamTabANodeBuilder(): NodeBuilder = tabANodeBuilder
      override fun createParamTabBNodeBuilder(): NodeBuilder = tabBNodeBuilder
    },
    schema = homeSchema,
  )
  val mainImportNodeBuilder = ParamMainImportNodeBuilder(
    nodeFactory = object : ParamMainImportNodeBuilder.Factory {
      override fun createRootNode(deeplink: String): FlowNode<*> = createParamMainImport(deeplink)
      override fun createParamHomeImportNodeBuilder(): NodeBuilder = homeImportNodeBuilder
      override fun createParamMainScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParallelTestParamswMainSchema(paramHomeImportSchema = homeSchema),
  )
  val sheetImportNodeBuilder = ParamSheetImportNodeBuilder(
    nodeFactory = object : ParamSheetImportNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = createParamSheetImport()
      override fun createParamSheetScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = ParallelTestParamswSheetSchema(),
  )
  val rootNodeBuilder = ParamAppRootNodeBuilder(
    nodeFactory = object : ParamAppRootNodeBuilder.Factory {
      override fun createRootNode(deeplink: String): ParallelFlowNode<*> = createParamAppRoot(deeplink)
      override fun createParamMainImportNodeBuilder(): NodeBuilder = mainImportNodeBuilder
      override fun createParamSheetImportNodeBuilder(): NodeBuilder = sheetImportNodeBuilder
    },
    schema = ParallelTestParamswSchema(
      paramMainImportSchema = ParallelTestParamswMainSchema(paramHomeImportSchema = homeSchema),
      paramSheetImportSchema = ParallelTestParamswSheetSchema(),
    ),
  )
  return NavigationService(nodeBuilder = rootNodeBuilder, onFinishRequest = { Ignore })
}
