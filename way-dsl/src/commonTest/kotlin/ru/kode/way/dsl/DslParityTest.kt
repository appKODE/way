package ru.kode.way.dsl

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import ru.kode.way.Event
import ru.kode.way.Finish
import ru.kode.way.Ignore
import ru.kode.way.NavigateTo
import ru.kode.way.NavigationService
import ru.kode.way.NavigationState
import ru.kode.way.NodeBuilder
import ru.kode.way.Target
import ru.kode.way.TestEvent
import ru.kode.way.TestFlowNode
import ru.kode.way.TestFlowNodeWithResult
import ru.kode.way.TestFlowTransitionSpec
import ru.kode.way.TestNodeBuilder
import ru.kode.way.TestParallelNode
import ru.kode.way.TestParallelTransitionSpec
import ru.kode.way.TestScreenNode
import ru.kode.way.dslparity.AppChildFinishRequest
import ru.kode.way.dslparity.AppNodeBuilder
import ru.kode.way.dslparity.DslParityAppSchema
import ru.kode.way.dslparity.app
import ru.kode.way.dslparity.feed.DslParityFeedSchema
import ru.kode.way.dslparity.feed.FeedNodeBuilder
import ru.kode.way.dslparity.feed.feed
import ru.kode.way.dslparity.home.DslParityHomeSchema
import ru.kode.way.dslparity.home.HomeNodeBuilder
import ru.kode.way.dslparity.home.home
import ru.kode.way.dslparity.login.DslParityLoginSchema
import ru.kode.way.dslparity.login.LoginNodeBuilder
import ru.kode.way.dslparity.login.login
import ru.kode.way.dslparity.settings
import ru.kode.way.dslparity.tabs.DslParityTabsSchema
import ru.kode.way.dslparity.tabs.TabsChildFinishRequest
import ru.kode.way.dslparity.tabs.TabsNodeBuilder
import ru.kode.way.tr

/** The DSL counterparts of `dsl-parity-*.dot`. */
private object LoginGraph : Graph("DslLogin") {
  val login = flow<Int>("login", parameterized = true)
  val credentials = screen("credentials")
  val otp = parameterizedScreen<Int>("otp")

  init {
    login to credentials to otp
  }
}

private object AppGraph : Graph("DslApp") {
  val app = flow<Unit>("app", parameterized = true)
  val intro = screen("intro")
  val details = parameterizedScreen<String?>("details")
  val settings = flow<String>("settings")
  val general = screen("general")
  val login = include<Int>("login", LoginGraph)

  init {
    app to intro to details
    app to settings to general
    app to login
  }
}

private object HomeGraph : Graph("DslHome") {
  val home = flow<Unit>("home")
  val homeScreen = screen("homeScreen")
  val homeDetails = screen("homeDetails")

  init {
    home to homeScreen to homeDetails
  }
}

private object FeedGraph : Graph("DslFeed") {
  val feed = flow<Unit>("feed")
  val feedScreen = screen("feedScreen")

  init {
    feed to feedScreen
  }
}

private object TabsGraph : Graph("DslTabs") {
  val tabs = parallel("tabs")
  val home = include<Unit>("home", HomeGraph)
  val feed = include<Unit>("feed", FeedGraph)

  init {
    tabs to home
    tabs to feed
  }
}

/** The targets and events which differ between a generated and a DSL graph. */
private class AppSpec(
  val intro: Target,
  val details: (String?) -> Target,
  val settings: Target,
  val general: Target,
  val login: (String) -> Target,
  val credentials: Target,
  val otp: (Int) -> Target,
  val isSettingsFinished: (Event) -> Boolean,
  val isLoginFinished: (Event) -> Boolean,
)

private fun appRootNode(spec: AppSpec, payload: Any?) = TestFlowNode(
  initialTarget = spec.intro,
  payload = payload,
  transitions = listOf(
    tr("D", spec.details(null)),
    tr("S", spec.settings),
    tr("L", spec.login("alice")),
    TestFlowTransitionSpec(spec.isSettingsFinished, NavigateTo(spec.details("from-settings"))),
    TestFlowTransitionSpec(spec.isLoginFinished, NavigateTo(spec.intro)),
  ),
)

private fun settingsNode(spec: AppSpec) = TestFlowNodeWithResult(
  initialTarget = spec.general,
  dismissResult = "dismissed",
  transitions = listOf(tr("F", Finish("done"))),
)

private fun loginNode(spec: AppSpec, payload: Any?) = TestFlowNode(
  initialTarget = spec.credentials,
  payload = payload,
  transitions = listOf(tr("O", spec.otp(7)), tr("F", Finish(5))),
)

private fun generatedAppBuilder(): NodeBuilder {
  val spec = AppSpec(
    intro = Target.app.intro,
    details = { Target.app.details(it) },
    settings = Target.app.settings,
    general = Target.settings.general,
    login = { Target.app.login(it) },
    credentials = Target.login.credentials,
    otp = { Target.login.otp(it) },
    isSettingsFinished = { it is AppChildFinishRequest.Settings && it.result == "done" },
    isLoginFinished = { it is AppChildFinishRequest.Login && it.result == 5 },
  )
  val schema = DslParityAppSchema(DslParityLoginSchema())
  return AppNodeBuilder(
    object : AppNodeBuilder.Factory {
      override fun createRootNode(timeout: Int) = appRootNode(spec, timeout)
      override fun createIntroNode() = TestScreenNode()
      override fun createDetailsNode(id: String?) = TestScreenNode(payload = id)

      // the generated SettingsNodeBuilder resolves "general" to app.settings.general under the "settings" alias and
      // fails to build it, so the local flow is built by hand
      override fun createSettingsNodeBuilder(): NodeBuilder =
        TestNodeBuilder(schema, mapOf("settings" to settingsNode(spec), "settings.general" to TestScreenNode()))

      override fun createLoginNodeBuilder(userName: String): NodeBuilder = LoginNodeBuilder(
        object : LoginNodeBuilder.Factory {
          override fun createRootNode(userName: String) = loginNode(spec, userName)
          override fun createCredentialsNode() = TestScreenNode()
          override fun createOtpNode(code: Int) = TestScreenNode(payload = code)
        },
        DslParityLoginSchema(),
      )
    },
    schema,
  )
}

private fun dslAppBuilder(): NodeBuilder {
  val spec = AppSpec(
    intro = AppGraph.intro.target(),
    details = { AppGraph.details.target(it) },
    settings = AppGraph.settings.target(),
    general = AppGraph.general.target(),
    login = { AppGraph.login.target(it) },
    credentials = LoginGraph.credentials.target(),
    otp = { LoginGraph.otp.target(it) },
    isSettingsFinished = { it.childFlowResult(AppGraph.settings) == "done" },
    isLoginFinished = { it.childFlowResult(AppGraph.login) == 5 },
  )
  return AppGraph.nodeBuilder {
    flow(AppGraph.app) { appRootNode(spec, it) }
    screen(AppGraph.intro) { TestScreenNode() }
    screen(AppGraph.details) { TestScreenNode(payload = it) }
    flow(AppGraph.settings) { settingsNode(spec) }
    screen(AppGraph.general) { TestScreenNode() }
    include(AppGraph.login) {
      LoginGraph.nodeBuilder {
        flow(LoginGraph.login) { loginNode(spec, it) }
        screen(LoginGraph.credentials) { TestScreenNode() }
        screen(LoginGraph.otp) { TestScreenNode(payload = it) }
      }
    }
  }
}

private fun tabsRootNode(isChildFinished: (Event) -> Boolean) = TestParallelNode(
  parallelTransitions = listOf(TestParallelTransitionSpec(isChildFinished, Finish(Unit))),
)

private fun homeNode(homeDetails: Target, homeScreen: Target) = TestFlowNode(
  initialTarget = homeScreen,
  transitions = listOf(tr("HD", homeDetails), tr("HF", Finish(Unit))),
)

private fun generatedTabsBuilder(): NodeBuilder = TabsNodeBuilder(
  object : TabsNodeBuilder.Factory {
    override fun createRootNode() = tabsRootNode { it == TabsChildFinishRequest.Home }
    override fun createHomeNodeBuilder(): NodeBuilder = HomeNodeBuilder(
      object : HomeNodeBuilder.Factory {
        override fun createRootNode() = homeNode(Target.home.homeDetails, Target.home.homeScreen)
        override fun createHomeScreenNode() = TestScreenNode()
        override fun createHomeDetailsNode() = TestScreenNode()
      },
      DslParityHomeSchema(),
    )

    override fun createFeedNodeBuilder(): NodeBuilder = FeedNodeBuilder(
      object : FeedNodeBuilder.Factory {
        override fun createRootNode() = TestFlowNode(initialTarget = Target.feed.feedScreen)
        override fun createFeedScreenNode() = TestScreenNode()
      },
      DslParityFeedSchema(),
    )
  },
  DslParityTabsSchema(DslParityHomeSchema(), DslParityFeedSchema()),
)

private fun dslTabsBuilder(): NodeBuilder = TabsGraph.nodeBuilder {
  parallel(TabsGraph.tabs) { tabsRootNode { it.childFlowResult(TabsGraph.home) != null } }
  include(TabsGraph.home) {
    HomeGraph.nodeBuilder {
      flow(HomeGraph.home) { homeNode(HomeGraph.homeDetails.target(), HomeGraph.homeScreen.target()) }
      screen(HomeGraph.homeScreen) { TestScreenNode() }
      screen(HomeGraph.homeDetails) { TestScreenNode() }
    }
  }
  include(TabsGraph.feed) {
    FeedGraph.nodeBuilder {
      flow(FeedGraph.feed) { TestFlowNode(initialTarget = FeedGraph.feedScreen.target()) }
      screen(FeedGraph.feedScreen) { TestScreenNode() }
    }
  }
}

/** Everything a test can observe after each step: per region the active path, the alive paths and their payloads. */
private fun NavigationState.describe(): List<String> = regions.values.map { region ->
  val payloads = region.alive.joinToString { path ->
    val payload = when (val node = region.nodes[path]) {
      is TestFlowNode -> node.payload
      is TestScreenNode -> node.payload
      else -> null
    }
    "$path=$payload"
  }
  "${region.active} [$payloads]"
}

/** Runs [events] on a service built by [nodeBuilder], returns the states after the start and after every event. */
private fun run(nodeBuilder: NodeBuilder, rootPayload: Any?, events: List<String>): Pair<List<List<String>>, Boolean> {
  var finished = false
  val service = NavigationService<Unit>(nodeBuilder, onFinishRequest = {
    finished = true
    Ignore
  })
  service.strictEventDropping = true
  val states = mutableListOf<List<String>>()
  service.addTransitionListener { states.add(it.describe()) }
  service.start(rootPayload)
  events.forEach { service.send(TestEvent(it)) }
  return states to finished
}

class DslParityTest :
  ShouldSpec({
    should("navigate a flow with an included graph as its generated counterpart does") {
      val events = listOf("D", "S", "F", "L", "O", "F")

      val (generated, _) = run(generatedAppBuilder(), rootPayload = 42, events)
      val (dsl, _) = run(dslAppBuilder(), rootPayload = 42, events)

      dsl shouldBe generated
      // as in the generated graph, finishing a flow emits two states
      dsl.map { it.single().substringBefore(' ') } shouldContainExactly listOf(
        "app.intro",
        "app.intro.details",
        "app.settings.general",
        "app.settings.general",
        "app.intro.details",
        "app.login.credentials",
        "app.login.credentials.otp",
        "app.login.credentials.otp",
        "app.intro",
      )
      dsl[4].single() shouldBe "app.intro.details [app=42, app.intro=null, app.intro.details=from-settings]"
      dsl[6].single() shouldBe
        "app.login.credentials.otp [app=42, app.login=alice, app.login.credentials=null, app.login.credentials.otp=7]"
    }

    should("navigate a parallel flow with included regions as its generated counterpart does") {
      val events = listOf("HD", "HF")

      val (generated, generatedFinished) = run(generatedTabsBuilder(), rootPayload = null, events)
      val (dsl, dslFinished) = run(dslTabsBuilder(), rootPayload = null, events)

      dsl shouldBe generated
      dsl.first().map { it.substringBefore(' ') } shouldContainExactly
        listOf("tabs.home.homeScreen", "tabs.feed.feedScreen")
      dsl[1].map { it.substringBefore(' ') } shouldContainExactly
        listOf("tabs.home.homeScreen.homeDetails", "tabs.feed.feedScreen")
      dslFinished shouldBe true
      generatedFinished shouldBe true
    }
  })
