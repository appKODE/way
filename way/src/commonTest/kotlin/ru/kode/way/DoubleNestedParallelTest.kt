package ru.kode.way

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import ru.kode.way.doublenested.DnANodeBuilder
import ru.kode.way.doublenested.DnASchema
import ru.kode.way.doublenested.DnAppNodeBuilder
import ru.kode.way.doublenested.DnBNodeBuilder
import ru.kode.way.doublenested.DnBSchema
import ru.kode.way.doublenested.DnInnerNodeBuilder
import ru.kode.way.doublenested.DnInnerSchema
import ru.kode.way.doublenested.DnLeftNodeBuilder
import ru.kode.way.doublenested.DnLeftSchema
import ru.kode.way.doublenested.DnMainNodeBuilder
import ru.kode.way.doublenested.DnMainSchema
import ru.kode.way.doublenested.DnOuterNodeBuilder
import ru.kode.way.doublenested.DnOuterSchema
import ru.kode.way.doublenested.DnRightNodeBuilder
import ru.kode.way.doublenested.DnRightSchema
import ru.kode.way.doublenested.DnSideNodeBuilder
import ru.kode.way.doublenested.DnSideSchema
import ru.kode.way.doublenested.DnWrapNodeBuilder
import ru.kode.way.doublenested.ParallelTestDoubleNestedSchema
import ru.kode.way.doublenested.dnA
import ru.kode.way.doublenested.dnB
import ru.kode.way.doublenested.dnLeft
import ru.kode.way.doublenested.dnMain
import ru.kode.way.doublenested.dnRight
import ru.kode.way.doublenested.dnSide
import ru.kode.way.doublenested.dnWrap

/** A parallel declared inside of a region of another parallel, see `parallel-test-double-nested.dot`. */
class DoubleNestedParallelTest : ShouldSpec() {
  init {
    should("start the regions of each parallel when it is entered") {
      val log = mutableListOf<String>()
      val states = mutableListOf<NavigationState>()
      val sut = buildService(log)
      sut.addTransitionListener { states.add(it) }
      sut.start()
      log.shouldBeEmpty()
      states.last().regionNames().shouldContainExactlyInAnyOrder("dnMain", "dnSide")

      sut.send(TestEvent("openOuter"))
      log.shouldContainExactly("enter wrap", "enter outer", "enter left", "enter right")
      states.last().regionNames().shouldContainExactlyInAnyOrder("dnMain", "dnSide", "dnLeft", "dnRight")

      log.clear()
      sut.send(TestEvent("openInner"))
      log.shouldContainExactlyInAnyOrder("enter inner", "enter a", "enter b")
      log.indexOf("enter inner") shouldBeLessThan log.indexOf("enter a")
      log.indexOf("enter inner") shouldBeLessThan log.indexOf("enter b")
      states.last().regionNames()
        .shouldContainExactlyInAnyOrder("dnMain", "dnSide", "dnLeft", "dnRight", "dnA", "dnB")
    }

    should("exit the inner parallel and its regions when only it is left") {
      val log = mutableListOf<String>()
      val states = mutableListOf<NavigationState>()
      val sut = buildService(log)
      sut.addTransitionListener { states.add(it) }
      sut.start()
      sut.send(TestEvent("openOuter"))
      sut.send(TestEvent("openInner"))

      log.clear()
      sut.send(TestEvent("closeInner"))
      log.shouldContainExactlyInAnyOrder("exit a", "exit b", "exit inner")
      log.last() shouldBe "exit inner"
      states.last().regionNames().shouldContainExactlyInAnyOrder("dnMain", "dnSide", "dnLeft", "dnRight")
    }

    should("exit both parallels from the deepest node up when the outer one is left") {
      val log = mutableListOf<String>()
      val states = mutableListOf<NavigationState>()
      val sut = buildService(log)
      sut.addTransitionListener { states.add(it) }
      sut.start()
      sut.send(TestEvent("openOuter"))
      sut.send(TestEvent("openInner"))

      log.clear()
      sut.send(TestEvent("openIntro"))
      log.shouldContainExactlyInAnyOrder(
        "exit a",
        "exit b",
        "exit inner",
        "exit left",
        "exit right",
        "exit outer",
        "exit wrap",
      )
      // a flow between the region and the parallel is above the parallel, so it is exited after it
      log.last() shouldBe "exit wrap"
      log.indexOf("exit a") shouldBeLessThan log.indexOf("exit inner")
      log.indexOf("exit b") shouldBeLessThan log.indexOf("exit inner")
      log.indexOf("exit inner") shouldBeLessThan log.indexOf("exit left")
      log.indexOf("exit left") shouldBeLessThan log.indexOf("exit outer")
      log.indexOf("exit right") shouldBeLessThan log.indexOf("exit outer")
      states.last().regionNames().shouldContainExactlyInAnyOrder("dnMain", "dnSide")

      // nothing of the left parallels is kept: they are entered anew, the inner one only when asked for
      log.clear()
      sut.send(TestEvent("openOuter"))
      log.shouldContainExactly("enter wrap", "enter outer", "enter left", "enter right")
      states.last().regionNames().shouldContainExactlyInAnyOrder("dnMain", "dnSide", "dnLeft", "dnRight")
    }

    listOf("a", "inner", "left", "outer", "wrap").forEach { failing ->
      should("keep both parallels when the onExit of \"$failing\" throws on leaving the outer one") {
        val all = listOf("a", "b", "inner", "left", "right", "outer", "wrap")
        val log = mutableListOf<String>()
        val states = mutableListOf<NavigationState>()
        val sut = buildService(log, failingOnExit = mutableSetOf(failing))
        sut.addTransitionListener { states.add(it) }
        sut.start()
        sut.send(TestEvent("openOuter"))
        sut.send(TestEvent("openInner"))
        val statesBefore = states.size

        log.clear()
        shouldThrow<IllegalStateException> { sut.send(TestEvent("openIntro")) }
        // every node which was exited is entered back, no node twice
        val exited = log.filter { it.startsWith("exit") }.map { it.removePrefix("exit ") }
        exited.shouldContainExactlyInAnyOrder(exited.distinct())
        log.filter { it.startsWith("enter") }.map { it.removePrefix("enter ") }.shouldContainExactlyInAnyOrder(exited)
        states.size shouldBe statesBefore

        // the transition was rolled back: nothing is lost and the same leave works afterwards
        log.clear()
        sut.send(TestEvent("openIntro"))
        log.shouldContainExactlyInAnyOrder(all.map { "exit $it" })
        log.indexOf("exit inner") shouldBeLessThan log.indexOf("exit left")
        log.indexOf("exit left") shouldBeLessThan log.indexOf("exit outer")
        log.last() shouldBe "exit wrap"
        states.last().regionNames().shouldContainExactlyInAnyOrder("dnMain", "dnSide")
      }
    }

    should("enter back a parallel which was left by a failed transition with its regions in place") {
      val log = mutableListOf<String>()
      val leftStartedOnEntry = mutableListOf<Boolean>()
      val sut = buildService(
        log,
        failingOnExit = mutableSetOf("wrap"),
        onOuterEntry = { leftStartedOnEntry += it.isRegionStarted(DnOuterSchema().dnLeftRegionId) },
      )
      sut.start()
      sut.send(TestEvent("openOuter"))

      leftStartedOnEntry.clear()
      shouldThrow<IllegalStateException> { sut.send(TestEvent("openIntro")) }
      leftStartedOnEntry.shouldContainExactly(true)
    }

    should("enter every node after the nodes above it when a target is deep inside of both parallels") {
      val log = mutableListOf<String>()
      val sut = buildService(log)
      sut.start()

      sut.send(TestEvent("openDeep"))
      log.shouldContainExactlyInAnyOrder(
        "enter wrap",
        "enter outer",
        "enter left",
        "enter right",
        "enter inner",
        "enter a",
        "enter b",
      )
      listOf(
        "wrap" to "outer",
        "outer" to "left",
        "outer" to "right",
        "left" to "inner",
        "inner" to "a",
        "inner" to "b",
      )
        .forEach { (above, below) -> log.indexOf("enter $above") shouldBeLessThan log.indexOf("enter $below") }
    }

    should("start a schema whose root is a flow with a parallel declared under it") {
      val log = mutableListOf<String>()
      val states = mutableListOf<NavigationState>()
      val sut = buildService(log, flowRooted = true)
      sut.addTransitionListener { states.add(it) }
      sut.start()
      log.shouldBeEmpty()
      states.last().regionNames().shouldContainExactly("dnMain")

      sut.send(TestEvent("openOuter"))
      log.shouldContainExactly("enter wrap", "enter outer", "enter left", "enter right")
      states.last().regionNames().shouldContainExactlyInAnyOrder("dnMain", "dnLeft", "dnRight")

      sut.send(TestEvent("openInner"))
      states.last().regionNames().shouldContainExactlyInAnyOrder("dnMain", "dnLeft", "dnRight", "dnA", "dnB")

      log.clear()
      sut.send(TestEvent("openIntro"))
      log.last() shouldBe "exit wrap"
      log.size shouldBe 7
      states.last().regionNames().shouldContainExactly("dnMain")
    }

    should("ask a parallel declared under a flow about Back once") {
      fun eventsOfOuterOnBack(flowRooted: Boolean): List<String> {
        val outerEvents = mutableListOf<Event>()
        val sut = buildService(mutableListOf(), flowRooted = flowRooted, onOuterEvent = { outerEvents.add(it) })
        sut.start()
        sut.send(TestEvent("openOuter"))
        outerEvents.clear()
        sut.send(Event.Back)
        return outerEvents.map { it.toString() }
      }

      // Back goes into one region, chosen by the parallel: its regions are not asked each on their own
      val events = eventsOfOuterOnBack(flowRooted = true)
      events.count { it == Event.Back.toString() } shouldBe 1
      events.size shouldBe 2
      eventsOfOuterOnBack(flowRooted = false) shouldBe events
    }

    should("exit a node whose onEntry has thrown along with the nodes entered before it") {
      val log = mutableListOf<String>()
      val states = mutableListOf<NavigationState>()
      val sut = buildService(log, failingOnEntry = mutableSetOf("left"))
      sut.addTransitionListener { states.add(it) }
      sut.start()

      shouldThrow<IllegalStateException> { sut.send(TestEvent("openOuter")) }
      log.shouldContainExactly("enter wrap", "enter outer", "enter left", "exit left", "exit outer", "exit wrap")
      states.last().regionNames().shouldContainExactlyInAnyOrder("dnMain", "dnSide")

      log.clear()
      sut.send(TestEvent("openOuter"))
      log.shouldContainExactly("enter wrap", "enter outer", "enter left", "enter right")
    }

    should("not exit a node whose onEntry was not reached because a hook has thrown before it") {
      val log = mutableListOf<String>()
      val sut = buildService(log)
      var failing = true
      sut.addNodeExtensionPoint(
        TestNodeExtensionPoint(
          preEntry = { _, path -> if (failing && "$path".endsWith("dnLeft")) error("hook failed") },
        ),
      )
      sut.start()

      shouldThrow<IllegalStateException> { sut.send(TestEvent("openOuter")) }
      log.shouldContainExactly("enter wrap", "enter outer", "exit outer", "exit wrap")

      failing = false
      log.clear()
      sut.send(TestEvent("openOuter"))
      log.shouldContainExactly("enter wrap", "enter outer", "enter left", "enter right")
    }

    should("not enter back a node whose onExit was not reached because a hook has thrown before it") {
      val log = mutableListOf<String>()
      val sut = buildService(log)
      var failing = false
      sut.addNodeExtensionPoint(
        TestNodeExtensionPoint(
          preExit = { _, path -> if (failing && "$path".endsWith("dnLeft")) error("hook failed") },
        ),
      )
      sut.start()
      sut.send(TestEvent("openOuter"))

      failing = true
      log.clear()
      shouldThrow<IllegalStateException> { sut.send(TestEvent("openIntro")) }
      log.filter { it.endsWith(" left") }.shouldBeEmpty()
      log.filter { it.startsWith("exit") }.map { it.removePrefix("exit") }.sorted() shouldBe
        log.filter { it.startsWith("enter") }.map { it.removePrefix("enter") }.sorted()

      failing = false
      log.clear()
      sut.send(TestEvent("openIntro"))
      log.filter { it.endsWith(" left") }.shouldContainExactly("exit left")
    }

    should("drop an event which a node has sent while it was entered back by a failed transition") {
      val log = mutableListOf<String>()
      lateinit var sut: NavigationService<Unit>
      var sendOnEntry = false
      sut = buildService(
        log,
        failingOnExit = mutableSetOf("wrap"),
        onOuterEntry = { if (sendOnEntry) sut.send(TestEvent("openIntro")) },
      )
      sut.start()
      sut.send(TestEvent("openOuter"))

      sendOnEntry = true
      shouldThrow<IllegalStateException> { sut.send(TestEvent("openIntro")) }
      sendOnEntry = false
      log.clear()
      sut.send(TestEvent("unknown"))
      log.shouldBeEmpty()
    }
  }
}

private fun NavigationState.regionNames(): List<String> = regions.keys.map { it.path.lastSegment().name }

private fun buildService(
  log: MutableList<String>,
  failingOnExit: MutableSet<String> = mutableSetOf(),
  failingOnEntry: MutableSet<String> = mutableSetOf(),
  flowRooted: Boolean = false,
  onOuterEntry: (ParallelFlowNode<*>) -> Unit = {},
  onOuterEvent: (Event) -> Unit = {},
): NavigationService<Unit> {
  fun onExit(name: String) {
    log += "exit $name"
    if (failingOnExit.remove(name)) error("exit of $name failed")
  }

  fun onEntry(name: String) {
    log += "enter $name"
    if (failingOnEntry.remove(name)) error("entry of $name failed")
  }

  fun flow(name: String, initialTarget: Target, transitions: List<TestFlowTransitionSpec> = emptyList()) = TestFlowNode(
    initialTarget = initialTarget,
    transitions = transitions,
    onEntryImpl = { onEntry(name) },
    onExitImpl = { onExit(name) },
  )

  fun parallel(name: String) = TestParallelNode(onEntryImpl = { onEntry(name) }, onExitImpl = { onExit(name) })

  val aNodeBuilder = DnANodeBuilder(
    nodeFactory = object : DnANodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = flow("a", Target.dnA.dnAScreen)
      override fun createDnAScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = DnASchema(),
  )
  val bNodeBuilder = DnBNodeBuilder(
    nodeFactory = object : DnBNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = flow("b", Target.dnB.dnBScreen)
      override fun createDnBScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = DnBSchema(),
  )
  val innerNodeBuilder = DnInnerNodeBuilder(
    nodeFactory = object : DnInnerNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<*> = parallel("inner")
      override fun createDnANodeBuilder(): NodeBuilder = aNodeBuilder
      override fun createDnBNodeBuilder(): NodeBuilder = bNodeBuilder
    },
    schema = DnInnerSchema(),
  )
  val leftNodeBuilder = DnLeftNodeBuilder(
    nodeFactory = object : DnLeftNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = flow(
        "left",
        Target.dnLeft.dnLeftScreen,
        listOf(tr("openInner", Target.dnA.dnAScreen), tr("closeInner", Target.dnLeft.dnLeftScreen)),
      )
      override fun createDnLeftScreenNode(): ScreenNode = TestScreenNode()
      override fun createDnInnerNodeBuilder(): NodeBuilder = innerNodeBuilder
    },
    schema = DnLeftSchema(),
  )
  val rightNodeBuilder = DnRightNodeBuilder(
    nodeFactory = object : DnRightNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = flow("right", Target.dnRight.dnRightScreen)
      override fun createDnRightScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = DnRightSchema(),
  )
  val outerNodeBuilder = DnOuterNodeBuilder(
    nodeFactory = object : DnOuterNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<*> {
        lateinit var node: ParallelFlowNode<*>
        node = TestParallelNode(
          onEntryImpl = {
            log += "enter outer"
            onOuterEntry(node)
          },
          onExitImpl = { onExit("outer") },
          onTransitionCallback = onOuterEvent,
        )
        return node
      }
      override fun createDnLeftNodeBuilder(): NodeBuilder = leftNodeBuilder
      override fun createDnRightNodeBuilder(): NodeBuilder = rightNodeBuilder
      override fun createDnInnerNodeBuilder(): NodeBuilder = innerNodeBuilder
    },
    schema = DnOuterSchema(),
  )
  val wrapNodeBuilder = DnWrapNodeBuilder(
    nodeFactory = object : DnWrapNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = flow("wrap", Target.dnWrap.dnWrapScreen)
      override fun createDnWrapScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = DnMainSchema(),
  )
  val mainNodeBuilder = DnMainNodeBuilder(
    nodeFactory = object : DnMainNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(
        initialTarget = Target.dnMain.dnIntroScreen,
        transitions = listOf(
          tr("openOuter", Target.dnLeft.dnLeftScreen),
          tr("openIntro", Target.dnMain.dnIntroScreen),
          tr("openDeep", Target.dnA.dnAScreen),
        ),
      )
      override fun createDnIntroScreenNode(): ScreenNode = TestScreenNode()
      override fun createDnWrapNodeBuilder(): NodeBuilder = wrapNodeBuilder
      override fun createDnOuterNodeBuilder(): NodeBuilder = outerNodeBuilder
    },
    schema = DnMainSchema(),
  )
  val sideNodeBuilder = DnSideNodeBuilder(
    nodeFactory = object : DnSideNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.dnSide.dnSideScreen)
      override fun createDnSideScreenNode(): ScreenNode = TestScreenNode()
    },
    schema = DnSideSchema(),
  )
  if (flowRooted) return NavigationService(nodeBuilder = mainNodeBuilder, onFinishRequest = { Ignore })
  return NavigationService(
    nodeBuilder = DnAppNodeBuilder(
      nodeFactory = object : DnAppNodeBuilder.Factory {
        override fun createRootNode(): ParallelFlowNode<*> = TestParallelNode()
        override fun createDnMainNodeBuilder(): NodeBuilder = mainNodeBuilder
        override fun createDnSideNodeBuilder(): NodeBuilder = sideNodeBuilder
        override fun createDnWrapNodeBuilder(): NodeBuilder = wrapNodeBuilder
        override fun createDnOuterNodeBuilder(): NodeBuilder = outerNodeBuilder
      },
      schema = ParallelTestDoubleNestedSchema(),
    ),
    onFinishRequest = { Ignore },
  )
}
