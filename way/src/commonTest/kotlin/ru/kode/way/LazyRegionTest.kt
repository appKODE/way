package ru.kode.way

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import ru.kode.way.extension.node.hook.BaseParallelFlowNode
import ru.kode.way.extension.node.hook.FlowNodeHook
import ru.kode.way.extension.node.hook.NodeHooksSupportExtensionPoint
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
import ru.kode.way.partoproot.AlphaNodeBuilder
import ru.kode.way.partoproot.AlphaSchema
import ru.kode.way.partoproot.BetaNodeBuilder
import ru.kode.way.partoproot.BetaSchema
import ru.kode.way.partoproot.ParallelTestTopRootSchema
import ru.kode.way.partoproot.TopRootNodeBuilder
import ru.kode.way.partoproot.alpha
import ru.kode.way.partoproot.beta

class LazyRegionTest : ShouldSpec() {
  init {
    should("start only the initial regions of a root parallel") {
      val fixture = TopRootFixture(initial = { setOf(it.alphaRegionId) })
      fixture.service.start()

      fixture.regionLeaves() shouldBe mapOf("alpha" to "alphaScreen")
      fixture.betaRootsCreated shouldBe 0
      fixture.root.isRegionStarted(fixture.schema.alphaRegionId) shouldBe true
      fixture.root.isRegionStarted(fixture.schema.betaRegionId) shouldBe false
    }

    should("start a region of a root parallel once and keep the started ones") {
      val fixture = TopRootFixture(initial = { setOf(it.alphaRegionId) })
      fixture.service.start()

      fixture.service.send(TestEvent("startBeta"))

      fixture.regionLeaves() shouldBe mapOf("alpha" to "alphaScreen", "beta" to "betaScreen")
      fixture.alphaRootEntries shouldBe 1
      fixture.betaRootEntries shouldBe 1
      fixture.root.isRegionStarted(fixture.schema.betaRegionId) shouldBe true

      fixture.service.send(TestEvent("startBeta"))
      fixture.betaRootEntries shouldBe 1
    }

    should("deliver events and Back while a region is not started") {
      val fixture = TopRootFixture(initial = { setOf(it.alphaRegionId) })
      fixture.service.start()

      fixture.service.send(TestEvent("unknown"))
      fixture.service.send(Event.Back)

      fixture.betaRootsCreated shouldBe 0
    }

    should("tell whether a region rests on its first node") {
      val fixture = Par03Fixture(initial = { setOf(it.par03BetaRegionId) })
      fixture.service.start()
      val main = fixture.main
      val alpha = fixture.mainSchema.par03AlphaRegionId

      main.isRegionAtRoot(alpha) shouldBe false
      fixture.service.send(TestEvent("startAlpha"))
      main.isRegionAtRoot(alpha) shouldBe true
      fixture.service.send(TestEvent("goToScreen2"))
      main.isRegionAtRoot(alpha) shouldBe false
      fixture.service.send(Event.Back)
      main.isRegionAtRoot(alpha) shouldBe true
    }

    should("call hooks of a parallel flow node") {
      val calls = mutableListOf<String>()
      val fixture = Par03Fixture(initial = { setOf(it.par03BetaRegionId) })
      fixture.main.addHook(object : FlowNodeHook<Unit> {
        override fun onPostEntry() {
          calls += "entry"
        }
        override fun onPostExit() {
          calls += "exit"
        }
      })
      fixture.service.start()
      fixture.service.send(TestEvent("PAGE"))

      calls shouldBe listOf("entry", "exit")
    }

    should("reject empty initial regions") {
      val fixture = TopRootFixture(initial = { emptySet() })
      shouldThrow<IllegalStateException> { fixture.service.start() }
    }

    should("start only the initial regions when a parallel is entered by navigation") {
      val fixture = Par03Fixture(initial = { setOf(it.par03BetaRegionId) })
      fixture.service.start()

      fixture.regionLeaves() shouldBe mapOf("par03App" to "par03Main", "par03Beta" to "par03BetaScreen")

      fixture.service.send(TestEvent("PAGE"))
      fixture.service.send(TestEvent("MAIN"))
      fixture.regionLeaves() shouldBe mapOf("par03App" to "par03Main", "par03Beta" to "par03BetaScreen")
    }

    should("not restart a started region of a nested parallel") {
      val fixture = Par03Fixture(initial = { setOf(it.par03BetaRegionId) })
      fixture.service.start()

      fixture.service.send(TestEvent("startAlpha"))
      fixture.regionLeaves()["par03Alpha"] shouldBe "par03AlphaScreen"
      fixture.betaRootEntries shouldBe 1

      fixture.service.send(TestEvent("goToScreen2"))
      fixture.service.send(TestEvent("startAlpha"))
      fixture.regionLeaves()["par03Alpha"] shouldBe "par03AlphaScreen2"
      fixture.betaRootEntries shouldBe 1
    }

    should("start a region by an absolute target without restarting its siblings") {
      val fixture = Par03Fixture(initial = { setOf(it.par03BetaRegionId) })
      fixture.service.start()

      fixture.service.send(TestEvent("deeplinkAlpha"))

      fixture.regionLeaves() shouldBe mapOf(
        "par03App" to "par03Main",
        "par03Alpha" to "par03AlphaScreen2",
        "par03Beta" to "par03BetaScreen",
      )
      fixture.betaRootEntries shouldBe 1
    }

    should("start a region by a history target") {
      val fixture = Par03Fixture(initial = { setOf(it.par03BetaRegionId) })
      fixture.service.start()

      fixture.service.send(TestEvent("historyAlpha"))

      fixture.regionLeaves() shouldBe mapOf(
        "par03App" to "par03Main",
        "par03Alpha" to "par03AlphaScreen",
        "par03Beta" to "par03BetaScreen",
      )
      fixture.main.isRegionStarted(fixture.mainSchema.par03AlphaRegionId) shouldBe true
      fixture.betaRootEntries shouldBe 1
    }

    should("ignore Back which is dispatched to a region which is not started") {
      val fixture = Par03Fixture(initial = { setOf(it.par03AlphaRegionId) }, backRegion = { it.par03BetaRegionId })
      fixture.service.start()
      fixture.service.send(TestEvent("goToScreen2"))

      fixture.service.send(Event.Back)

      fixture.regionLeaves() shouldBe mapOf("par03App" to "par03Main", "par03Alpha" to "par03AlphaScreen2")
    }

    should("reject initial regions of another parallel") {
      val fixture =
        TopRootFixture(initial = {
          setOf(Parallel03MainSchema(Parallel03AlphaSchema(), Parallel03BetaSchema()).par03AlphaRegionId)
        })
      shouldThrow<IllegalStateException> { fixture.service.start() }
    }

    should("start the initial regions and the target one when an absolute target enters a parallel") {
      val fixture = Par03Fixture(initial = { setOf(it.par03BetaRegionId) })
      fixture.service.start()
      fixture.service.send(TestEvent("PAGE"))
      fixture.regionLeaves().keys.shouldContainOnly("par03App")

      fixture.service.send(TestEvent("deeplinkBeta"))
      fixture.regionLeaves() shouldBe mapOf("par03App" to "par03Main", "par03Beta" to "par03BetaScreen")

      fixture.service.send(TestEvent("PAGE"))
      fixture.service.send(TestEvent("deeplinkAlpha"))
      fixture.regionLeaves() shouldBe mapOf(
        "par03App" to "par03Main",
        "par03Alpha" to "par03AlphaScreen2",
        "par03Beta" to "par03BetaScreen",
      )
    }
  }
}

internal class LazyParallelNode(
  private val initial: Set<RegionId>,
  private val startEvents: Map<String, RegionId>,
  private val backRegion: RegionId? = null,
) : BaseParallelFlowNode<Unit>() {
  override val dismissResult = Unit
  override val initialRegions: Set<RegionId> get() = initial

  override fun transition(event: Event): FlowTransition<Unit> {
    if (event == Event.Back && backRegion != null) return DispatchBackTo(backRegion)
    val regionId = (event as? TestEvent)?.let { startEvents[it.name] }
    return if (regionId != null) startRegion(regionId) else Ignore
  }
}

private fun NavigationState.leaves(): Map<String, String> =
  regions.entries.associate { it.key.path.lastSegment().name to it.value.active.lastSegment().name }

private class TopRootFixture(initial: (ParallelTestTopRootSchema) -> Set<RegionId>) {
  val schema = ParallelTestTopRootSchema()
  val root = LazyParallelNode(initial(schema), mapOf("startBeta" to schema.betaRegionId))
  var alphaRootEntries = 0
  var betaRootEntries = 0
  var betaRootsCreated = 0
  private val states = mutableListOf<NavigationState>()

  val service: NavigationService<Unit> = NavigationService<Unit>(
    nodeBuilder = TopRootNodeBuilder(
      nodeFactory = object : TopRootNodeBuilder.Factory {
        override fun createRootNode(): ParallelFlowNode<Unit> = root
        override fun createAlphaNodeBuilder(): NodeBuilder = AlphaNodeBuilder(
          nodeFactory = object : AlphaNodeBuilder.Factory {
            override fun createRootNode(): FlowNode<*> =
              TestFlowNode(initialTarget = Target.alpha.alphaScreen, onEntryImpl = { alphaRootEntries++ })
            override fun createAlphaScreenNode(): ScreenNode = TestScreenNode()
          },
          schema = AlphaSchema(),
        )
        override fun createBetaNodeBuilder(): NodeBuilder = BetaNodeBuilder(
          nodeFactory = object : BetaNodeBuilder.Factory {
            override fun createRootNode(): FlowNode<*> {
              betaRootsCreated++
              return TestFlowNode(initialTarget = Target.beta.betaScreen, onEntryImpl = { betaRootEntries++ })
            }
            override fun createBetaScreenNode(): ScreenNode = TestScreenNode()
          },
          schema = BetaSchema(),
        )
      },
      schema = schema,
    ),
    onFinishRequest = { Ignore },
  ).also { service -> service.addTransitionListener { states.add(it) } }

  fun regionLeaves(): Map<String, String> = states.last().leaves()
}

private class Par03Fixture(
  initial: (Parallel03MainSchema) -> Set<RegionId>,
  backRegion: (Parallel03MainSchema) -> RegionId? = { null },
) {
  val mainSchema = Parallel03MainSchema(Parallel03AlphaSchema(), Parallel03BetaSchema())
  private val appSchema = Parallel03Schema(par03MainSchema = mainSchema)
  var betaRootEntries = 0
  private val states = mutableListOf<NavigationState>()

  val main = LazyParallelNode(
    initial(mainSchema),
    mapOf("startAlpha" to mainSchema.par03AlphaRegionId),
    backRegion(mainSchema),
  )

  private val alphaNodeBuilder = Par03AlphaNodeBuilder(
    nodeFactory = object : Par03AlphaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.par03Alpha.par03AlphaScreen,
        transitions = listOf(tr("goToScreen2", Target.par03Alpha.par03AlphaScreen2)),
      )
      override fun createPar03AlphaScreenNode(): ScreenNode = TestScreenNode()
      override fun createPar03AlphaScreen2Node(): ScreenNode = TestScreenNode()
    },
    schema = Parallel03AlphaSchema(),
  )
  private val betaNodeBuilder = Par03BetaNodeBuilder(
    nodeFactory = object : Par03BetaNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> =
        TestFlowNode(initialTarget = Target.par03Beta.par03BetaScreen, onEntryImpl = { betaRootEntries++ })
      override fun createPar03BetaScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = Parallel03BetaSchema(),
  )
  private val mainNodeBuilder = Par03MainNodeBuilder(
    nodeFactory = object : Par03MainNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = main
      override fun createPar03AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
      override fun createPar03BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
    },
    schema = mainSchema,
  )

  private val mainPath = Target.par03App.par03Main.path.let { Path(appSchema.rootSegment).append(it) }

  val service: NavigationService<Unit> = NavigationService<Unit>(
    nodeBuilder = Par03AppNodeBuilder(
      nodeFactory = object : Par03AppNodeBuilder.Factory {
        override fun createRootNode(): FlowNode<*> = object : FlowNode<Unit> {
          override val initial: Target = Target.par03App.par03Main
          override val dismissResult = Unit
          override fun transition(event: Event): FlowTransition<Unit> = when ((event as? TestEvent)?.name) {
            "PAGE" -> NavigateTo(Target.par03App.par03Page)

            "MAIN" -> NavigateTo(Target.par03App.par03Main)

            "deeplinkAlpha" -> NavigateTo(
              AbsoluteTarget(
                mainSchema.par03AlphaRegionId.resolveAbsolute(mainPath).path
                  .append(Target.par03Alpha.par03AlphaScreen2.path),
              ),
            )

            "historyAlpha" -> NavigateTo(HistoryTarget(mainSchema.par03AlphaRegionId.resolveAbsolute(mainPath).path))

            "deeplinkBeta" -> NavigateTo(
              AbsoluteTarget(
                mainSchema.par03BetaRegionId.resolveAbsolute(mainPath).path
                  .append(Target.par03Beta.par03BetaScreen.path),
              ),
            )

            else -> Ignore
          }
        }
        override fun createPar03MainNodeBuilder(): NodeBuilder = mainNodeBuilder
        override fun createPar03PageNode(): ScreenNode = TestScreenNode()
      },
      schema = appSchema,
    ),
    onFinishRequest = { Ignore },
  ).also { service ->
    service.addNodeExtensionPoint(NodeHooksSupportExtensionPoint())
    service.addTransitionListener { states.add(it) }
  }

  fun regionLeaves(): Map<String, String> = states.last().leaves()
}
