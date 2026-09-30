package ru.kode.way

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import ru.kode.way.extension.node.hook.BaseFlowNode
import ru.kode.way.extension.node.hook.BaseScreenNode
import ru.kode.way.nav05.NavService05Schema
import ru.kode.way.nav10.AppChildFinishRequest
import ru.kode.way.nav10.AppNodeBuilder
import ru.kode.way.nav10.LoginNodeBuilder
import ru.kode.way.nav10.NavService10LoginSchema
import ru.kode.way.nav10.NavService10PermissionsSchema
import ru.kode.way.nav10.NavService10Schema
import ru.kode.way.nav10.PermissionsNodeBuilder
import ru.kode.way.nav10.app
import ru.kode.way.nav10.login
import ru.kode.way.nav10.permissions
import ru.kode.way.nav12.NavService12LoginSchema
import ru.kode.way.nav12.NavService12Schema
import ru.kode.way.nav15.NavService15Schema
import ru.kode.way.parlazyint.ParallelTestLazyIntermediateSchema
import ru.kode.way.parlazyint.inner.ParallelTestLazyIntermediateInnerSchema
import ru.kode.way.parlazyint.inner.leftTab
import ru.kode.way.parlazyint.inner.rightTab
import ru.kode.way.parlazyint.outerApp
import ru.kode.way.partoproot.ParallelTestTopRootSchema
import java.nio.charset.Charset
import ru.kode.way.nav05.app as app05
import ru.kode.way.nav12.AppNodeBuilder as Nav12AppNodeBuilder
import ru.kode.way.nav12.LoginNodeBuilder as Nav12LoginNodeBuilder
import ru.kode.way.nav12.app as app12
import ru.kode.way.nav12.login as login12
import ru.kode.way.nav15.AppNodeBuilder as Nav15AppNodeBuilder
import ru.kode.way.nav15.app as app15
import ru.kode.way.partoproot.alpha as topRootAlpha
import ru.kode.way.partoproot.beta as topRootBeta

class NodeEventSinkTest :
  ShouldSpec({
    // app -> page1 -> login -> credentials -> permissions -> intro. "E" enters login, "P" opens permissions,
    // "L" finishes login, which brings app back to page1. Page1 and login are Base*Node, recorded when entered.
    class Fixture(private val loginOnEntry: (EventSink) -> Unit = {}) {
      val dropped = mutableListOf<Pair<Event, DropReason>>()
      val states = mutableListOf<NavigationState>()
      val page1Nodes = mutableListOf<BaseScreenNode>()
      val loginNodes = mutableListOf<BaseFlowNode<Int>>()
      val sinksSeenOnEntry = mutableListOf<EventSink>()

      val sut = NavigationService(
        AppNodeBuilder(
          object : AppNodeBuilder.Factory {
            override fun createRootNode() = TestFlowNode(
              initialTarget = Target.app.page1,
              transitions = listOf(
                tr("E", Target.app.login),
                tr<AppChildFinishRequest.Login>(NavigateTo(Target.app.page1)),
              ),
            )

            override fun createPage1Node() = object : BaseScreenNode() {
              override fun onEntry(event: Event) {
                sinksSeenOnEntry.add(eventSink)
                page1Nodes.add(this)
              }
            }

            override fun createPage2Node() = TestScreenNode()
            override fun createLoginNodeBuilder(): NodeBuilder = LoginNodeBuilder(
              object : LoginNodeBuilder.Factory {
                override fun createRootNode() = object : BaseFlowNode<Int>() {
                  override val initial: Target = Target.login.credentials
                  override val dismissResult: Int = 0

                  override fun onEntry(event: Event) {
                    sinksSeenOnEntry.add(eventSink)
                    loginNodes.add(this)
                    loginOnEntry(eventSink)
                  }

                  override fun transition(event: Event): FlowTransition<Int> = when (event) {
                    TestEvent("P") -> NavigateTo(Target.login.permissions)
                    TestEvent("L") -> Finish(7)
                    else -> Ignore
                  }
                }

                override fun createCredentialsNode() = TestScreenNode()
                override fun createPermissionsNodeBuilder(): NodeBuilder = PermissionsNodeBuilder(
                  object : PermissionsNodeBuilder.Factory {
                    override fun createRootNode() = TestFlowNodeWithResult(Target.permissions.intro, "")
                    override fun createIntroNode() = TestScreenNode()
                  },
                  NavService10PermissionsSchema(),
                )
              },
              NavService10LoginSchema(NavService10PermissionsSchema()),
            )
          },
          NavService10Schema(NavService10LoginSchema(NavService10PermissionsSchema())),
        ),
        onFinishRequest = { _: Unit -> Ignore },
      )

      init {
        sut.addTransitionListener { states.add(it) }
        sut.addServiceExtensionPoint(
          object : ServiceExtensionPoint<Unit> {
            override fun onPreTransition(service: NavigationService<Unit>, event: Event, state: NavigationState) = Unit

            override fun onPostTransition(service: NavigationService<Unit>, event: Event, state: NavigationState) = Unit

            override fun onEventDropped(service: NavigationService<Unit>, event: Event, reason: DropReason) {
              dropped.add(event to reason)
            }
          },
        )
        sut.start()
      }

      val active get() = states.last().regions.values.single().active.toString()
    }

    should("be an EventSink whose send resolves events from the whole tree") {
      val f = Fixture()
      val sink: EventSink = f.sut

      sink.send(TestEvent("E"))

      f.dropped.shouldBeEmpty()
      f.active shouldBe "app.page1.login.credentials"
    }

    should("attach a usable eventSink to BaseScreenNode and BaseFlowNode before onEntry") {
      val f = Fixture()
      f.page1Nodes.single().eventSink.send(TestEvent("E"))
      f.active shouldBe "app.page1.login.credentials"
      f.loginNodes.single().eventSink.send(TestEvent("P"))

      f.dropped.shouldBeEmpty()
      f.active shouldBe "app.page1.login.credentials.permissions.intro"
      f.sinksSeenOnEntry.size shouldBe 2
      f.sinksSeenOnEntry[0] shouldBeSameInstanceAs f.page1Nodes.single().eventSink
      f.sinksSeenOnEntry[1] shouldBeSameInstanceAs f.loginNodes.single().eventSink
    }

    should("apply an event sent through a flow node's eventSink from its onEntry after the current dispatch") {
      val f = Fixture(loginOnEntry = { it.send(TestEvent("P")) })

      f.sut.send(TestEvent("E"))

      f.dropped.shouldBeEmpty()
      f.active shouldBe "app.page1.login.credentials.permissions.intro"
    }

    should("apply a deferred send through a flow node's eventSink while the flow is still alive") {
      val f = Fixture()
      f.sut.send(TestEvent("E"))
      val deferred = f.loginNodes.single().eventSink
      f.sut.send(TestEvent("P"))

      deferred.send(TestEvent("L"))

      f.dropped.shouldBeEmpty()
      f.active shouldBe "app.page1"
    }

    should("drop an event sent through a node's eventSink after the node exited as StaleSource") {
      val f = Fixture()
      f.sut.send(TestEvent("E"))
      val login = f.loginNodes.single()
      f.sut.send(TestEvent("L"))
      val transitions = f.states.size

      login.eventSink.send(TestEvent("P"))

      f.dropped shouldBe listOf(TestEvent("P") to DropReason.StaleSource(login.nodePath))
      f.states.size shouldBe transitions
      f.active shouldBe "app.page1"
    }

    should("give a re-entered node position a fresh eventSink while the old one stays stale") {
      val f = Fixture()
      f.sut.send(TestEvent("E"))
      f.sut.send(TestEvent("L"))
      f.sut.send(TestEvent("E"))
      val (first, second) = f.loginNodes

      first.eventSink.send(TestEvent("P"))
      f.dropped.size shouldBe 1
      second.eventSink.send(TestEvent("P"))

      f.dropped.size shouldBe 1
      f.active shouldBe "app.page1.login.credentials.permissions.intro"
    }

    should("throw when eventSink is read before the node was entered") {
      shouldThrow<IllegalStateException> { object : BaseScreenNode() {}.eventSink }
        .message shouldContain "before the runtime calls onEntry"
      shouldThrow<IllegalStateException> {
        object : BaseFlowNode<Unit>() {
          override val initial: Target = Target.app.page1
          override val dismissResult = Unit
        }.eventSink
      }.message shouldContain "before the runtime calls onEntry"
    }
    should("attach a sink to a root ParallelFlowNode and a region-root BaseFlowNode entered during start()") {
      val consulted = mutableListOf<String>()
      val alpha = object : BaseFlowNode<Unit>() {
        override val initial: Target = Target.topRootAlpha.alphaScreen
        override val dismissResult = Unit

        override fun onEntry(event: Event) = eventSink.send(TestEvent("A"))

        override fun transition(event: Event): FlowTransition<Unit> {
          if (event is TestEvent) consulted.add("alpha ${event.name}")
          return if (event == TestEvent("A")) Stay else Ignore
        }
      }
      lateinit var parallel: TestParallelNode
      parallel = TestParallelNode(
        onEntryImpl = { parallel.eventSink.send(TestEvent("P")) },
        onTransitionCallback = { if (it is TestEvent) consulted.add("parallel ${it.name}") },
      )
      val sut = NavigationService(
        TestNodeBuilder(
          ParallelTestTopRootSchema(),
          mapOf(
            "topRoot" to parallel,
            "topRoot.alpha" to alpha,
            "topRoot.alpha.alphaScreen" to TestScreenNode(),
            "topRoot.beta" to TestFlowNode(initialTarget = Target.topRootBeta.betaScreen),
            "topRoot.beta.betaScreen" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      val drops = sut.recordDrops()

      sut.start()

      drops.shouldBeEmpty()
      consulted shouldContain "alpha A"
      consulted shouldContain "parallel P"
      consulted.clear()
      parallel.eventSink.send(TestEvent("Q"))
      alpha.eventSink.send(TestEvent("A"))
      drops.shouldBeEmpty()
      consulted shouldContain "parallel Q"
      consulted shouldContain "alpha A"
    }

    should("make a ParallelFlowNode's eventSink stale once the parallel left and live again on its next entry") {
      val consulted = mutableListOf<String>()
      val parallel = TestParallelNode(onTransitionCallback = { if (it is TestEvent) consulted.add(it.name) })
      val sut = NavigationService(
        TestNodeBuilder(
          ParallelTestLazyIntermediateSchema(importedParallelSchema = ParallelTestLazyIntermediateInnerSchema()),
          mapOf(
            "outerApp" to TestFlowNode(
              initialTarget = Target.outerApp.outerScreen,
              transitions = listOf(
                tr("in", Target.outerApp.importedParallel),
                tr("out", Target.outerApp.outerScreen),
              ),
            ),
            "outerApp.outerScreen" to TestScreenNode(),
            "outerApp.importedParallel" to parallel,
            "outerApp.importedParallel.leftTab" to TestFlowNode(initialTarget = Target.leftTab.leftScreen),
            "outerApp.importedParallel.leftTab.leftScreen" to TestScreenNode(),
            "outerApp.importedParallel.rightTab" to TestFlowNode(initialTarget = Target.rightTab.rightScreen),
            "outerApp.importedParallel.rightTab.rightScreen" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      val drops = sut.recordDrops()
      sut.start()
      sut.send(TestEvent("in"))
      val sink = parallel.eventSink

      sink.send(TestEvent("X"))
      drops.shouldBeEmpty()
      consulted shouldContain "X"

      sut.send(TestEvent("out"))
      sink.send(TestEvent("Y"))
      (drops.single() as DropReason.StaleSource).path.lastSegment().name shouldBe "importedParallel"
      consulted shouldNotContain "Y"

      sut.send(TestEvent("in"))
      (parallel.eventSink === sink) shouldBe false
      parallel.eventSink.send(TestEvent("Z"))
      drops.size shouldBe 1
      consulted shouldContain "Z"
    }

    should("make the old instance's sink stale and the new one's live when an alive node is re-targeted") {
      val details = mutableListOf<BaseScreenNode>()
      val states = mutableListOf<NavigationState>()
      val sut = NavigationService(
        Nav15AppNodeBuilder(
          object : Nav15AppNodeBuilder.Factory {
            override fun createRootNode() = TestFlowNode(
              initialTarget = Target.app15.main,
              transitions = listOf(
                tr("D", Target.app15.details(id = "d1")),
                tr("R", Target.app15.details(id = "d9")),
                tr("T", Target.app15.packageDetails(pid = "p1")),
              ),
            )

            override fun createMainNode() = TestScreenNode()
            override fun createDetailsNode(id: String) = object : BaseScreenNode() {}.also { details.add(it) }
            override fun createPackageDetailsNode(pid: String) = TestScreenNode()
          },
          NavService15Schema(),
        ),
        onFinishRequest = { _: Int -> Ignore },
      )
      val drops = sut.recordDrops()
      sut.addTransitionListener { states.add(it) }
      sut.start()
      sut.send(TestEvent("D"))
      sut.send(TestEvent("R"))
      val (old, new) = details

      old.eventSink.send(TestEvent("T"))
      (drops.single() as DropReason.StaleSource).path.toString() shouldBe "app.main.details"
      new.eventSink.send(TestEvent("T"))

      drops.size shouldBe 1
      states.last().active shouldBe "app.main.details.packageDetails"
    }

    should("keep an exited node's sink live after a rollback and make the rolled-back entry's sink stale") {
      val intro = object : BaseScreenNode() {}
      lateinit var mainSink: EventSink
      val main = object : BaseScreenNode() {
        override fun onEntry(event: Event) {
          mainSink = eventSink
          eventSink.send(TestEvent("toTest"))
          error("boom")
        }
      }
      val f = Nav05Fixture(intro, main)
      val introSink = intro.eventSink
      val transitions = f.states.size

      shouldThrow<IllegalStateException> { f.sut.send(TestEvent("toMain")) }.message shouldBe "boom"

      // the event main sent from its rolled-back onEntry is discarded, not dropped
      f.drops.shouldBeEmpty()
      f.states.size shouldBe transitions
      intro.eventSink shouldBeSameInstanceAs introSink
      mainSink.send(TestEvent("toTest"))
      (f.drops.single() as DropReason.StaleSource).path.lastSegment().name shouldBe "main"
      f.states.size shouldBe transitions
      intro.eventSink.send(TestEvent("toTest"))
      f.drops.size shouldBe 1
      f.states.last().active shouldBe "app.test"
    }

    should("replace the sink of a node instance the builder returns again on re-entry") {
      val intro = object : BaseScreenNode() {}
      val f = Nav05Fixture(intro, TestScreenNode())
      val captured = intro.eventSink
      f.sut.send(TestEvent("toMain"))
      f.sut.send(TestEvent("toIntro"))

      (intro.eventSink === captured) shouldBe false
      captured.send(TestEvent("toTest"))
      (f.drops.single() as DropReason.StaleSource).path.lastSegment().name shouldBe "intro"
      f.states.last().active shouldBe "app.intro"
      intro.eventSink.send(TestEvent("toTest"))
      f.drops.size shouldBe 1
      f.states.last().active shouldBe "app.test"
    }

    should("give a node rebuilt by a HistoryTarget a fresh live sink") {
      val credentials = mutableListOf<BaseScreenNode>()
      val states = mutableListOf<NavigationState>()
      val appSchema = NavService12Schema(NavService12LoginSchema())
      val loginPath = Target.app12.login(defaultUserName = "unused").path.prepend(appSchema.rootSegment)
      val sut = NavigationService(
        Nav12AppNodeBuilder(
          object : Nav12AppNodeBuilder.Factory {
            override fun createRootNode(timeout: Int) = TestFlowNode(
              initialTarget = Target.app12.page1(Charsets.UTF_8),
              transitions = listOf(
                tr("L", Target.app12.login(defaultUserName = "alice")),
                tr("P2", Target.app12.page2),
                tr("S", HistoryTarget(loginPath, deep = false)),
              ),
            )

            override fun createPage2Node() = TestScreenNode()
            override fun createPage1Node(charset: Charset) = TestScreenNode()
            override fun createLoginNodeBuilder(defaultUserName: String): NodeBuilder = Nav12LoginNodeBuilder(
              object : Nav12LoginNodeBuilder.Factory {
                override fun createRootNode(defaultUserName: String) = TestFlowNode(
                  initialTarget = Target.login12.credentials(defaultPhone = "+700"),
                  transitions = listOf(tr("O", Target.login12.otp(useAnimation = true))),
                )

                override fun createCredentialsNode(defaultPhone: String) = object : BaseScreenNode() {
                  override fun onEntry(event: Event) {
                    credentials.add(this)
                  }
                }

                override fun createOtpNode(useAnimation: Boolean) = TestScreenNode()
              },
              NavService12LoginSchema(),
            )
          },
          appSchema,
        ),
        onFinishRequest = { _: Int -> Ignore },
      )
      val drops = sut.recordDrops()
      sut.addTransitionListener { states.add(it) }
      sut.start(42)
      sut.send(TestEvent("L"))
      sut.send(TestEvent("P2"))

      sut.send(TestEvent("S"))

      states.last().active shouldBe "app.page1.login.credentials"
      val (old, restored) = credentials
      (old === restored) shouldBe false
      old.eventSink.send(TestEvent("O"))
      (drops.single() as DropReason.StaleSource).path.lastSegment().name shouldBe "credentials"
      restored.eventSink.send(TestEvent("O"))
      drops.size shouldBe 1
      states.last().active shouldBe "app.page1.login.credentials.otp"
    }
  })

private fun <R : Any> NavigationService<R>.recordDrops(): MutableList<DropReason> {
  val drops = mutableListOf<DropReason>()
  addServiceExtensionPoint(
    object : ServiceExtensionPoint<R> {
      override fun onPreTransition(service: NavigationService<R>, event: Event, state: NavigationState) = Unit
      override fun onPostTransition(service: NavigationService<R>, event: Event, state: NavigationState) = Unit
      override fun onEventDropped(service: NavigationService<R>, event: Event, reason: DropReason) {
        drops.add(reason)
      }
    },
  )
  return drops
}

// nav05: app -> intro | main | test, the builder returns the same instances on every build
private class Nav05Fixture(intro: Node, main: Node) {
  val states = mutableListOf<NavigationState>()
  val sut = NavigationService(
    TestNodeBuilder(
      NavService05Schema(),
      mapOf(
        "app" to TestFlowNode(
          initialTarget = Target.app05.intro,
          transitions = listOf(
            tr("toMain", Target.app05.main),
            tr("toIntro", Target.app05.intro),
            tr("toTest", Target.app05.test),
          ),
        ),
        "app.intro" to intro,
        "app.main" to main,
        "app.test" to TestScreenNode(),
      ),
    ),
    onFinishRequest = { _: Unit -> Stay },
  )
  val drops = sut.recordDrops()

  init {
    sut.addTransitionListener { states.add(it) }
    sut.start()
  }
}
