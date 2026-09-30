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
    // "R" re-targets details with another argument, "N" is a full target which misses the packageDetails payload
    fun createService(
      hideParameters: Boolean = false,
      initial: () -> Target = { Target.app15.main },
    ): NavigationService<Int> {
      val nodeBuilder = Nav15AppNodeBuilder(
        object : Nav15AppNodeBuilder.Factory {
          override fun createRootNode() = TestFlowNode(
            initialTarget = initial(),
            transitions = listOf(
              tr("D", Target.app15.details(id = "d1")),
              tr("M", Target.app15.main),
              tr("T", Target.app15.packageDetails(pid = "p1")),
              tr("F", Target.app15.packageDetails(id = "d2", pid = "p2")),
              tr("X", Target.app15.main),
              tr("R", Target.app15.details(id = "d9")),
              tr("N", Target.app15.packageDetails(id = "d2", pid = "p2").copy(payload = null)),
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
      val hooks = mutableListOf<String>()

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
            override fun onPreTransition(service: NavigationService<Int>, event: Event, state: NavigationState) {
              hooks.add("pre $event")
            }

            override fun onPostTransition(service: NavigationService<Int>, event: Event, state: NavigationState) {
              hooks.add("post $event")
            }

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
        hooks.clear()
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

    should("not throw in strict mode for a stale sink event, only report it") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.start()
      sut.sendEvent(TestEvent("D"))
      val details = recorder.states.last().activePath()
      val sink = sut.eventSink(details)
      sut.sendEvent(TestEvent("M"))
      recorder.clear()
      sut.strictEventDropping = true

      sink.send(TestEvent("T"))

      recorder.dropped.single() shouldBe (TestEvent("T") to DropReason.StaleSource(details))
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

    should("call neither onPreTransition nor onPostTransition for a dropped event") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.start()
      sut.sendEvent(TestEvent("D"))
      val sink = sut.eventSink(recorder.states.last().activePath())
      sut.sendEvent(TestEvent("M"))
      recorder.clear()

      // MissingPayload: the pre-check runs before onPreTransition
      sut.sendEvent(TestEvent("T"))
      // StaleSource
      sink.send(TestEvent("D"))

      recorder.dropped.map { it.second::class } shouldBe listOf(
        DropReason.MissingPayload::class,
        DropReason.StaleSource::class,
      )
      recorder.hooks.shouldBeEmpty()
    }

    should("call only onPreTransition for an event dropped by the node builder backstop") {
      val sut = createService(hideParameters = true)
      val recorder = Recorder(sut)
      sut.openDetailsAndGoBack(recorder)

      sut.sendEvent(TestEvent("T"))

      recorder.dropped.single().first shouldBe TestEvent("T")
      recorder.hooks shouldBe listOf("pre ${TestEvent("T")}")
    }

    should("deliver events of sinks obtained in onEntry, of the root at start and of a node entered later") {
      val sut = createService()
      val recorder = Recorder(sut)
      val sinks = mutableMapOf<String, EventSink>()
      sut.addNodeExtensionPoint(
        TestNodeExtensionPoint(postEntry = { _, path ->
          sinks[path.toString()] =
            sut.eventSink(path)
        }),
      )
      sut.start()

      sinks.getValue("app").send(TestEvent("D"))
      recorder.states.last().active shouldBe "app.main.details"

      sinks.getValue("app.main.details").send(TestEvent("T"))

      recorder.dropped.shouldBeEmpty()
      recorder.states.last().active shouldBe "app.main.details.packageDetails"
    }

    should("roll back generations and discard events sent from onEntry when a transition is dropped") {
      val sut = createService(hideParameters = true)
      val recorder = Recorder(sut)
      var detailsSink: EventSink? = null
      var detailsPath: Path? = null
      sut.addNodeExtensionPoint(
        TestNodeExtensionPoint(postEntry = { _, path ->
          if (path.toString() == "app.main.details") {
            detailsPath = path
            detailsSink = sut.eventSink(path)
            sut.sendEvent(TestEvent("X"))
          }
        }),
      )
      sut.start()
      recorder.clear()

      // details is entered (and sends "X"), then building packageDetails throws: the transition is rolled back
      sut.sendEvent(TestEvent("N"))

      recorder.dropped.single().first shouldBe TestEvent("N")
      recorder.lifecycle shouldBe listOf("entry app.main.details", "exit app.main.details")
      // "X" was enqueued by the rolled back transition and is discarded together with it
      recorder.states.shouldBeEmpty()
      sut.nodeGeneration(detailsPath!!) shouldBe null
      detailsSink!!.send(TestEvent("T"))
      recorder.dropped.last().second shouldBe DropReason.StaleSource(detailsPath!!)
    }

    // sends "M" and enqueues "T" (dropped: details is not alive) and "D" while "M" is dispatched
    fun NavigationService<Int>.sendMainEnqueuingDropAndDetails() {
      // armed only for the dispatch of "M": addTransitionListener also calls the listener with the current state
      var armed = false
      addTransitionListener {
        if (armed) {
          armed = false
          sendEvent(TestEvent("T"))
          sendEvent(TestEvent("D"))
        }
      }
      armed = true
      sendEvent(TestEvent("M"))
    }

    should("throw in strict mode for an enqueued event, keeping the events queued after it") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.openDetailsAndGoBack(recorder)
      sut.strictEventDropping = true

      val e = shouldThrow<EventDroppedException> { sut.sendMainEnqueuingDropAndDetails() }

      e.event shouldBe TestEvent("T")
      recorder.states.map { it.active } shouldBe listOf("app.main")
      recorder.lifecycle.shouldBeEmpty()

      // the next sendEvent processes its own event first and then drains the queued "D"
      sut.sendEvent(TestEvent("X"))

      recorder.states.map { it.active } shouldBe listOf("app.main", "app.main", "app.main.details")
    }

    should("propagate an exception of onEventDropped, keeping the state and the queued events") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.openDetailsAndGoBack(recorder)
      sut.addServiceExtensionPoint(
        object : ServiceExtensionPoint<Int> {
          override fun onPreTransition(service: NavigationService<Int>, event: Event, state: NavigationState) = Unit
          override fun onPostTransition(service: NavigationService<Int>, event: Event, state: NavigationState) = Unit
          override fun onEventDropped(service: NavigationService<Int>, event: Event, reason: DropReason): Unit =
            throw IllegalArgumentException("dropped $event")
        },
      )

      val e = shouldThrow<IllegalArgumentException> { sut.sendMainEnqueuingDropAndDetails() }

      e.message shouldBe "dropped ${TestEvent("T")}"
      recorder.states.map { it.active } shouldBe listOf("app.main")
      recorder.lifecycle.shouldBeEmpty()

      sut.sendEvent(TestEvent("X"))

      recorder.states.map { it.active } shouldBe listOf("app.main", "app.main", "app.main.details")
    }

    should("rethrow a missing payload of the initial target from start, without reporting a drop, and allow a retry") {
      var initial: Target = Target.app15.packageDetails(pid = "p1")
      val sut = createService(initial = { initial })
      val recorder = Recorder(sut)
      recorder.clear()

      val e = shouldThrow<MissingPayloadException> { sut.start() }

      e.path.toString() shouldBe "app.main.details"
      recorder.dropped.shouldBeEmpty()
      recorder.states.shouldBeEmpty()
      recorder.lifecycle.count { it.startsWith("exit") } shouldBe recorder.lifecycle.count { it.startsWith("entry") }

      initial = Target.app15.packageDetails(id = "d1", pid = "p1")
      sut.start()

      recorder.states.single().apply {
        active shouldBe "app.main.details.packageDetails"
        (aliveNodes["app.main.details"] as TestScreenNode).payload shouldBe "d1"
      }
    }

    should("rebuild an alive parameterized node re-targeted with another argument, making its old sink stale") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.start()
      sut.sendEvent(TestEvent("D"))
      val details = recorder.states.last().activePath()
      val oldNode = recorder.states.last().aliveNodes["app.main.details"]
      val oldSink = sut.eventSink(details)
      recorder.clear()

      sut.sendEvent(TestEvent("R"))

      recorder.lifecycle shouldBe listOf("exit app.main.details", "entry app.main.details")
      recorder.states.single().apply {
        active shouldBe "app.main.details"
        val newNode = aliveNodes["app.main.details"] as TestScreenNode
        (newNode === oldNode) shouldBe false
        newNode.payload shouldBe "d9"
      }
      oldSink.send(TestEvent("T"))
      recorder.dropped.single().second shouldBe DropReason.StaleSource(details)
      sut.eventSink(details).send(TestEvent("T"))
      recorder.states.last().active shouldBe "app.main.details.packageDetails"
    }

    should("keep an alive node re-targeted with an equal argument and rebuild only the re-targeted leaf") {
      val sut = createService()
      val recorder = Recorder(sut)
      sut.start()
      sut.sendEvent(TestEvent("D"))
      val details = recorder.states.last().activePath()
      val sink = sut.eventSink(details)
      val node = recorder.states.last().aliveNodes["app.main.details"]
      val generation = sut.nodeGeneration(details)
      recorder.clear()

      // the same argument
      sut.sendEvent(TestEvent("D"))

      recorder.lifecycle.shouldBeEmpty()
      (recorder.states.single().aliveNodes["app.main.details"] === node) shouldBe true
      sut.nodeGeneration(details) shouldBe generation

      // packageDetails gets another argument, alive details keeps its own one ("F" carries "d2" for it)
      sut.sendEvent(TestEvent("T"))
      recorder.clear()
      sut.sendEvent(TestEvent("F"))

      recorder.lifecycle shouldBe
        listOf("exit app.main.details.packageDetails", "entry app.main.details.packageDetails")
      recorder.states.single().apply {
        (aliveNodes["app.main.details"] === node) shouldBe true
        (aliveNodes["app.main.details.packageDetails"] as TestScreenNode).payload shouldBe "p2"
      }
      sink.send(TestEvent("X"))
      recorder.dropped.shouldBeEmpty()
      recorder.states.last().active shouldBe "app.main"
    }

    should("pass the plain sink event to the enqueued events scheduler and keep its source when it is sent back") {
      val sut = createService()
      val recorder = Recorder(sut)
      val scheduled = mutableListOf<Event>()
      sut.setEnqueuedEventsScheduler { scheduled.add(it) }
      sut.start()
      var armed = false
      sut.addTransitionListener { state ->
        if (armed) {
          armed = false
          sut.eventSink(state.activePath()).send(TestEvent("T"))
        }
      }
      armed = true
      sut.sendEvent(TestEvent("D"))
      val details = recorder.states.last().activePath()

      scheduled shouldBe listOf(TestEvent("T"))
      sut.sendEvent(scheduled.removeAt(0))
      recorder.states.last().active shouldBe "app.main.details.packageDetails"

      // resent after its node has left: the same instance is still checked against its source
      sut.eventSink(details).send(TestEvent("M"))
      recorder.states.last().active shouldBe "app.main"
      armed = true
      sut.sendEvent(TestEvent("D"))
      val event = scheduled.single()
      sut.sendEvent(TestEvent("M"))
      recorder.dropped.shouldBeEmpty()

      sut.sendEvent(event)

      recorder.dropped.single() shouldBe (TestEvent("T") to DropReason.StaleSource(details))
    }
  })
