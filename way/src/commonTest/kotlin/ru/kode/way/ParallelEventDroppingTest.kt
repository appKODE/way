package ru.kode.way

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import ru.kode.way.pardrop.DropAlphaNodeBuilder
import ru.kode.way.pardrop.DropAlphaSchema
import ru.kode.way.pardrop.DropBetaNodeBuilder
import ru.kode.way.pardrop.DropBetaSchema
import ru.kode.way.pardrop.DropRootChildFinishRequest
import ru.kode.way.pardrop.DropRootNodeBuilder
import ru.kode.way.pardrop.ParallelTestDroppingSchema
import ru.kode.way.pardrop.dropAlpha
import ru.kode.way.pardrop.dropBeta

class ParallelEventDroppingTest :
  ShouldSpec({
    val alphaRegion = ParallelTestDroppingSchema().dropAlphaRegionId
    val betaRegion = ParallelTestDroppingSchema().dropBetaRegionId
    val rootPath = alphaRegion.path.take(1)
    // betaItem is not alive and the target carries no payload for it. A root parallel resolves relative targets in its
    // first region only, so beta is targeted absolutely
    val betaDetailsWithoutItem = AbsoluteTarget(betaRegion.path.append(Target.dropBeta.betaDetails.path))

    class Fixture(val sut: NavigationService<Unit>, val rootEvents: List<Event>) {
      val states = mutableListOf<NavigationState>()
      val lifecycle = mutableListOf<String>()
      val consulted = mutableListOf<String>()
      val dropped = mutableListOf<Pair<Event, DropReason>>()
      val hooks = mutableListOf<String>()

      init {
        sut.addTransitionListener { states.add(it) }
        sut.addNodeExtensionPoint(
          TestNodeExtensionPoint(
            preEntry = { _, path -> lifecycle.add("entry $path") },
            preExit = { _, path -> lifecycle.add("exit $path") },
            preTransition = { _, path, _ -> consulted.add(path.toString()) },
          ),
        )
        sut.addServiceExtensionPoint(
          object : ServiceExtensionPoint<Unit> {
            override fun onPreTransition(service: NavigationService<Unit>, event: Event, state: NavigationState) {
              hooks.add("pre $event")
            }

            override fun onPostTransition(service: NavigationService<Unit>, event: Event, state: NavigationState) {
              hooks.add("post $event")
            }

            override fun onEventDropped(service: NavigationService<Unit>, event: Event, reason: DropReason) {
              dropped.add(event to reason)
            }
          },
        )
      }

      val state get() = states.last()

      fun pathIn(regionId: RegionId): Path = state.regions.getValue(regionId).active

      fun activeIn(regionId: RegionId): String = pathIn(regionId).toString()

      fun clear() {
        states.subList(0, states.size - 1).clear()
        lifecycle.clear()
        consulted.clear()
        dropped.clear()
        hooks.clear()
      }
    }

    fun buildService(
      rootTransitions: List<TestFlowTransitionSpec> = emptyList(),
      rootParallelTransitions: List<TestParallelTransitionSpec> = emptyList(),
      alphaTransitions: List<TestFlowTransitionSpec> = emptyList(),
      alphaHomeTransitions: List<TestScreenTransitionSpec> = emptyList(),
      betaHomeTransitions: List<TestScreenTransitionSpec> = emptyList(),
    ): Fixture {
      val rootEvents = mutableListOf<Event>()
      val rootBuilder = DropRootNodeBuilder(
        object : DropRootNodeBuilder.Factory {
          override fun createRootNode(): ParallelFlowNode<*> = TestParallelNode(
            transitions = rootTransitions,
            parallelTransitions = rootParallelTransitions,
            onTransitionCallback = { rootEvents.add(it) },
          )

          override fun createDropAlphaNodeBuilder(): NodeBuilder = DropAlphaNodeBuilder(
            object : DropAlphaNodeBuilder.Factory {
              override fun createRootNode() = TestFlowNode(
                initialTarget = Target.dropAlpha.alphaHome,
                transitions = alphaTransitions,
              )

              override fun createAlphaHomeNode() = TestScreenNode(transitions = alphaHomeTransitions)
              override fun createAlphaItemNode(id: String) = TestScreenNode(payload = id)
            },
            DropAlphaSchema(),
          )

          override fun createDropBetaNodeBuilder(): NodeBuilder = DropBetaNodeBuilder(
            object : DropBetaNodeBuilder.Factory {
              override fun createRootNode() = TestFlowNode(initialTarget = Target.dropBeta.betaHome)
              override fun createBetaHomeNode() = TestScreenNode(transitions = betaHomeTransitions)
              override fun createBetaItemNode(id: String) = TestScreenNode(payload = id)
              override fun createBetaDetailsNode() = TestScreenNode()
            },
            DropBetaSchema(),
          )
        },
        ParallelTestDroppingSchema(),
      )
      val sut = NavigationService<Unit>(rootBuilder, onFinishRequest = { Ignore })
      return Fixture(sut, rootEvents)
    }

    // "A": alpha to alphaItem, "B": beta to betaDetails, deeper than alpha
    val openBoth = listOf(
      trs("A", Target.dropAlpha.alphaItem(id = "a1")),
    ) to listOf(
      trs("B", Target.dropBeta.betaDetails(id = "b1")),
    )

    should("route a Back sent through the root parallel sink by its DispatchBackTo, not to the deepest region") {
      val f = buildService(
        rootParallelTransitions = listOf(trp<BackEvent>(DispatchBackTo(alphaRegion))),
        alphaHomeTransitions = openBoth.first,
        betaHomeTransitions = openBoth.second,
      )
      f.sut.start()
      f.sut.sendEvent(TestEvent("A"))
      f.sut.sendEvent(TestEvent("B"))
      f.clear()

      f.sut.eventSink(rootPath).send(Event.Back)

      f.dropped.shouldBeEmpty()
      f.activeIn(alphaRegion) shouldBe "dropRoot.dropAlpha.alphaHome"
      f.activeIn(betaRegion) shouldBe "dropRoot.dropBeta.betaHome.betaItem.betaDetails"
    }

    should("resolve an event sent through the root parallel sink exactly like sendEvent") {
      fun fixture() = buildService(
        rootTransitions = listOf(tr("G", Stay)),
        alphaHomeTransitions = listOf(trs("G", Target.dropAlpha.alphaItem(id = "g"))),
        betaHomeTransitions = listOf(trs("G", Target.dropBeta.betaItem(id = "g"))),
      ).apply { sut.start() }
      val viaSink = fixture()
      val direct = fixture()

      viaSink.sut.eventSink(rootPath).send(TestEvent("G"))
      direct.sut.sendEvent(TestEvent("G"))

      viaSink.dropped.shouldBeEmpty()
      viaSink.consulted shouldBe direct.consulted
      viaSink.states shouldBe direct.states
      viaSink.activeIn(alphaRegion) shouldBe "dropRoot.dropAlpha.alphaHome.alphaItem"
      viaSink.activeIn(betaRegion) shouldBe "dropRoot.dropBeta.betaHome.betaItem"
    }

    should("keep a Back sent through a sub-region leaf sink in that region") {
      val f = buildService(
        alphaHomeTransitions = openBoth.first,
        betaHomeTransitions = openBoth.second,
      )
      f.sut.start()
      f.sut.sendEvent(TestEvent("A"))
      f.sut.sendEvent(TestEvent("B"))
      val alphaItem = f.pathIn(alphaRegion)
      f.clear()

      // a Back sent directly would pop the deeper beta region
      f.sut.eventSink(alphaItem).send(Event.Back)

      f.dropped.shouldBeEmpty()
      f.activeIn(alphaRegion) shouldBe "dropRoot.dropAlpha.alphaHome"
      f.activeIn(betaRegion) shouldBe "dropRoot.dropBeta.betaHome.betaItem.betaDetails"
    }

    should("broadcast an event enqueued by a transition which was started from a sink") {
      val f = buildService(
        alphaTransitions = listOf(tr("Q", EnqueueEvent(TestEvent("C")))),
        alphaHomeTransitions = listOf(trs("C", Target.dropAlpha.alphaItem(id = "c"))),
        betaHomeTransitions = listOf(trs("C", Target.dropBeta.betaItem(id = "c"))),
      )
      f.sut.start()
      val alphaHome = f.pathIn(alphaRegion)

      f.sut.eventSink(alphaHome).send(TestEvent("Q"))

      f.dropped.shouldBeEmpty()
      f.activeIn(alphaRegion) shouldBe "dropRoot.dropAlpha.alphaHome.alphaItem"
      f.activeIn(betaRegion) shouldBe "dropRoot.dropBeta.betaHome.betaItem"
    }

    should("deliver the child finish request of a sub-region finished from a sink to the root parallel") {
      val f = buildService(alphaTransitions = listOf(tr("F", Finish(Unit))))
      f.sut.start()
      val alphaHome = f.pathIn(alphaRegion)

      f.sut.eventSink(alphaHome).send(TestEvent("F"))

      f.dropped.shouldBeEmpty()
      f.rootEvents.last() shouldBe DropRootChildFinishRequest.DropAlpha
    }

    should("report the public child finish request when the root parallel handling it misses a payload") {
      val f = buildService(
        rootParallelTransitions = listOf(
          trp<DropRootChildFinishRequest.DropAlpha>(NavigateTo(betaDetailsWithoutItem)),
        ),
        alphaTransitions = listOf(tr("F", Finish(Unit))),
      )
      f.sut.start()
      f.clear()

      f.sut.sendEvent(TestEvent("F"))

      f.dropped.single().first shouldBe DropRootChildFinishRequest.DropAlpha
      (f.dropped.single().second as DropReason.MissingPayload).path.toString() shouldBe
        "dropRoot.dropBeta.betaHome.betaItem"
      f.hooks.none { it == "pre ${DropRootChildFinishRequest.DropAlpha}" } shouldBe true
      // "F" itself and the internal root finish request are applied, the child finish request is dropped
      f.states.size shouldBe 3
      f.activeIn(betaRegion) shouldBe "dropRoot.dropBeta.betaHome"
    }

    should("drop a multi-target transition entirely when one target misses a payload") {
      val f = buildService(
        rootTransitions = listOf(
          tr("M", NavigateTo(listOf(Target.dropAlpha.alphaItem(id = "m"), betaDetailsWithoutItem))),
        ),
      )
      f.sut.start()
      f.clear()

      f.sut.sendEvent(TestEvent("M"))

      (f.dropped.single().second as DropReason.MissingPayload).path.toString() shouldBe
        "dropRoot.dropBeta.betaHome.betaItem"
      f.states.size shouldBe 1
      f.lifecycle.shouldBeEmpty()
      f.hooks.shouldBeEmpty()
      f.activeIn(alphaRegion) shouldBe "dropRoot.dropAlpha.alphaHome"
    }

    should("drop a broadcast event entirely when a later region misses a payload, before any lifecycle call") {
      val f = buildService(
        alphaHomeTransitions = listOf(trs("X", Target.dropAlpha.alphaItem(id = "x"))),
        betaHomeTransitions = listOf(trs("X", Target.dropBeta.betaDetails)),
      )
      f.sut.start()
      f.clear()

      f.sut.sendEvent(TestEvent("X"))

      f.dropped.single().first shouldBe TestEvent("X")
      (f.dropped.single().second as DropReason.MissingPayload).path.toString() shouldBe
        "dropRoot.dropBeta.betaHome.betaItem"
      f.states.size shouldBe 1
      f.lifecycle.shouldBeEmpty()
      f.hooks.shouldBeEmpty()
      f.activeIn(alphaRegion) shouldBe "dropRoot.dropAlpha.alphaHome"
    }
  })
