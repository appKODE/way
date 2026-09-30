package ru.kode.way

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import ru.kode.way.nav15.NavService15Schema
import ru.kode.way.nav15.AppNodeBuilder as Nav15AppNodeBuilder
import ru.kode.way.nav15.app as app15

class EventDroppingTest :
  ShouldSpec({
    // "D" opens details, "M" goes back to main, "T" is the tap on details which opens packageDetails with a short
    // target, "F" does the same with a full target
    fun createService(hideParameters: Boolean = false): NavigationService<Int> {
      val nodeBuilder = Nav15AppNodeBuilder(
        object : Nav15AppNodeBuilder.Factory {
          override fun createRootNode() = TestFlowNode(
            initialTarget = Target.app15.main,
            transitions = listOf(
              tr("D", Target.app15.details(id = "d1")),
              tr("M", Target.app15.main),
              tr("T", Target.app15.packageDetails(pid = "p1")),
              tr("F", Target.app15.packageDetails(id = "d2", pid = "p2")),
              tr("X", Target.app15.main),
            ),
          )

          override fun createMainNode() = TestScreenNode()
          override fun createDetailsNode(id: String) = TestScreenNode(payload = id)
          override fun createPackageDetailsNode(pid: String) = TestScreenNode(payload = pid)
        },
        NavService15Schema(),
      )
      if (!hideParameters) return NavigationService(nodeBuilder, onFinishRequest = { _: Int -> Ignore })
      // a hand-rolled schema which doesn't report parameters: the pre-check lets a transition through and the
      // generated node builder throws while building a node
      val schema = object : Schema by nodeBuilder.schema {
        override fun isParameterized(regionId: RegionId, path: Path, rootSegmentAlias: Segment?) = false
      }
      return NavigationService(
        object : NodeBuilder by nodeBuilder {
          override val schema = schema
        },
        onFinishRequest = { _: Int -> Ignore },
      )
    }

    class Recorder(service: NavigationService<Int>) {
      val states = mutableListOf<NavigationState>()
      val lifecycle = mutableListOf<String>()
      val dropped = mutableListOf<Pair<Event, DropReason>>()

      init {
        service.addTransitionListener { states.add(it) }
        service.addNodeExtensionPoint(
          TestNodeExtensionPoint(
            preEntry = { _, path -> lifecycle.add("entry $path") },
            preExit = { _, path -> lifecycle.add("exit $path") },
          ),
        )
        service.addServiceExtensionPoint(
          object : ServiceExtensionPoint<Int> {
            override fun onPreTransition(service: NavigationService<Int>, event: Event, state: NavigationState) = Unit
            override fun onPostTransition(service: NavigationService<Int>, event: Event, state: NavigationState) = Unit
            override fun onEventDropped(service: NavigationService<Int>, event: Event, reason: DropReason) {
              dropped.add(event to reason)
            }
          },
        )
      }

      fun clear() {
        states.clear()
        lifecycle.clear()
        dropped.clear()
      }
    }

    fun NavigationService<Int>.openDetailsAndGoBack(recorder: Recorder) {
      start()
      sendEvent(TestEvent("D"))
      recorder.states.last().active shouldBe "app.main.details"
      sendEvent(TestEvent("M"))
      recorder.states.last().active shouldBe "app.main"
      recorder.clear()
    }

    should("drop a short target event sent after its parameterized ancestor was left, without lifecycle churn") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.openDetailsAndGoBack(recorder)

      sut.sendEvent(TestEvent("T"))

      recorder.dropped.size shouldBe 1
      recorder.dropped.single().first shouldBe TestEvent("T")
      (recorder.dropped.single().second as DropReason.MissingPayload).path.toString() shouldBe "app.main.details"
      recorder.states.shouldBeEmpty()
      recorder.lifecycle.shouldBeEmpty()

      // the service is still usable and keeps its state
      sut.sendEvent(TestEvent("D"))
      recorder.states.last().active shouldBe "app.main.details"
    }

    should("reach a child of an alive parameterized flow with a short target, keeping the ancestor payload") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.start()
      sut.sendEvent(TestEvent("D"))

      sut.sendEvent(TestEvent("T"))

      recorder.dropped.shouldBeEmpty()
      recorder.states.last().apply {
        active shouldBe "app.main.details.packageDetails"
        (aliveNodes["app.main.details"] as TestScreenNode).payload shouldBe "d1"
        (aliveNodes["app.main.details.packageDetails"] as TestScreenNode).payload shouldBe "p1"
      }
    }

    should("recreate a not alive parameterized ancestor with a full target") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.openDetailsAndGoBack(recorder)

      sut.sendEvent(TestEvent("F"))

      recorder.dropped.shouldBeEmpty()
      recorder.states.last().apply {
        active shouldBe "app.main.details.packageDetails"
        (aliveNodes["app.main.details"] as TestScreenNode).payload shouldBe "d2"
        (aliveNodes["app.main.details.packageDetails"] as TestScreenNode).payload shouldBe "p2"
      }
    }

    should("throw in strict mode and keep the state as it was before the event") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.openDetailsAndGoBack(recorder)
      sut.strictEventDropping = true

      val e = shouldThrow<EventDroppedException> { sut.sendEvent(TestEvent("T")) }

      e.event shouldBe TestEvent("T")
      (e.reason as DropReason.MissingPayload).path.toString() shouldBe "app.main.details"
      recorder.dropped.size shouldBe 1
      recorder.states.shouldBeEmpty()
      recorder.lifecycle.shouldBeEmpty()

      sut.strictEventDropping = false
      sut.sendEvent(TestEvent("D"))
      recorder.states.single().apply {
        active shouldBe "app.main.details"
        alive shouldBe listOf("app", "app.main", "app.main.details")
      }
    }

    should("drop the event when the node builder misses a payload the schema did not report") {
      val sut = createService(hideParameters = true)
      val recorder = Recorder(sut)
      sut.openDetailsAndGoBack(recorder)

      sut.sendEvent(TestEvent("T"))

      (recorder.dropped.single().second as DropReason.MissingPayload).path.toString() shouldBe "app.main.details"
      recorder.states.shouldBeEmpty()
      // the rollback compensates every exit with an entry
      recorder.lifecycle.count { it.startsWith("exit") } shouldBe recorder.lifecycle.count { it.startsWith("entry") }

      sut.sendEvent(TestEvent("D"))
      recorder.states.single().apply {
        active shouldBe "app.main.details"
        (aliveNodes["app.main.details"] as TestScreenNode).payload shouldBe "d1"
      }
    }

    should("keep draining enqueued events after a dropped one") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.openDetailsAndGoBack(recorder)
      // armed only for the dispatch of "M": addTransitionListener also calls the listener with the current state
      var armed = false
      sut.addTransitionListener {
        if (armed) {
          armed = false
          sut.sendEvent(TestEvent("T"))
          sut.sendEvent(TestEvent("D"))
        }
      }

      armed = true
      sut.sendEvent(TestEvent("M"))

      recorder.dropped.single().first shouldBe TestEvent("T")
      recorder.states.map { it.active } shouldBe listOf("app.main", "app.main.details")
    }

    fun NavigationState.activePath(): Path = regions.values.single().active

    should("drop an event sent through the sink of a node which was left, without lifecycle churn") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.start()
      sut.sendEvent(TestEvent("D"))
      val details = recorder.states.last().activePath()
      val sink = sut.eventSink(details)
      sut.sendEvent(TestEvent("M"))
      recorder.clear()

      sink.send(TestEvent("T"))

      recorder.dropped.single() shouldBe (TestEvent("T") to DropReason.StaleSource(details))
      recorder.states.shouldBeEmpty()
      recorder.lifecycle.shouldBeEmpty()
    }

    should("drop a sink event which was enqueued while its node was alive but dispatched after it was left") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.start()
      var armed = false
      var details: Path? = null
      sut.addTransitionListener { state ->
        if (armed) {
          armed = false
          details = state.activePath()
          val sink = sut.eventSink(state.activePath())
          sut.sendEvent(TestEvent("M"))
          sink.send(TestEvent("T"))
        }
      }
      recorder.clear()

      armed = true
      sut.sendEvent(TestEvent("D"))

      recorder.dropped.single() shouldBe (TestEvent("T") to DropReason.StaleSource(details!!))
      recorder.states.map { it.active } shouldBe listOf("app.main.details", "app.main")
    }

    should("deliver a sink event of an alive node, starting from that node rather than from the active leaf") {
      val sut = createService()
      val recorder = Recorder(sut)
      val consulted = mutableListOf<String>()
      sut.addNodeExtensionPoint(
        TestNodeExtensionPoint(preTransition = { _, path, _ ->
          consulted.add(path.toString())
        }),
      )
      sut.start()
      sut.sendEvent(TestEvent("D"))
      val details = recorder.states.last().activePath()
      sut.sendEvent(TestEvent("T"))
      recorder.states.last().active shouldBe "app.main.details.packageDetails"
      consulted.clear()

      sut.eventSink(details).send(TestEvent("X"))

      recorder.dropped.shouldBeEmpty()
      consulted shouldBe listOf("app.main.details", "app.main", "app")
      recorder.states.last().active shouldBe "app.main"
    }

    should("give a recreated node a new generation, so the sink of its previous instance is stale") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.start()
      sut.sendEvent(TestEvent("D"))
      val details = recorder.states.last().activePath()
      val oldSink = sut.eventSink(details)
      sut.sendEvent(TestEvent("M"))
      sut.sendEvent(TestEvent("D"))
      val newSink = sut.eventSink(details)
      recorder.clear()

      oldSink.send(TestEvent("T"))

      recorder.dropped.single().second shouldBe DropReason.StaleSource(details)
      recorder.states.shouldBeEmpty()

      newSink.send(TestEvent("T"))

      recorder.dropped.size shouldBe 1
      recorder.states.last().active shouldBe "app.main.details.packageDetails"
    }

    should("throw in strict mode for a stale sink event") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.start()
      sut.sendEvent(TestEvent("D"))
      val details = recorder.states.last().activePath()
      val sink = sut.eventSink(details)
      sut.sendEvent(TestEvent("M"))
      recorder.clear()
      sut.strictEventDropping = true

      val e = shouldThrow<EventDroppedException> { sink.send(TestEvent("T")) }

      e.event shouldBe TestEvent("T")
      e.reason shouldBe DropReason.StaleSource(details)
      recorder.states.shouldBeEmpty()
      recorder.lifecycle.shouldBeEmpty()
    }

    should("treat a sink of a path which is not alive as stale") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.start()
      val details = Path("app", "main", "details")
      recorder.clear()

      sut.eventSink(details).send(TestEvent("D"))

      recorder.dropped.single().second shouldBe DropReason.StaleSource(details)
      recorder.states.shouldBeEmpty()
    }
  })
