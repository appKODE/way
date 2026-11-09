package ru.kode.way.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
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
import ru.kode.way.TestParallelNode
import ru.kode.way.TestParallelTransitionSpec
import ru.kode.way.TestScreenNode
import ru.kode.way.dslparity.AppChildFinishRequest
import ru.kode.way.dslparity.AppNodeBuilder
import ru.kode.way.dslparity.DslParityAppSchema
import ru.kode.way.dslparity.SettingsNodeBuilder
import ru.kode.way.dslparity.app
import ru.kode.way.dslparity.counter.CounterNodeBuilder
import ru.kode.way.dslparity.counter.DslParityCounterSchema
import ru.kode.way.dslparity.counter.counter
import ru.kode.way.dslparity.fanin.DslParityFanInSchema
import ru.kode.way.dslparity.fanin.SharedChildFinishRequest
import ru.kode.way.dslparity.fanin.SharedNodeBuilder
import ru.kode.way.dslparity.fanin.shared
import ru.kode.way.dslparity.feed.DslParityFeedSchema
import ru.kode.way.dslparity.feed.FeedNodeBuilder
import ru.kode.way.dslparity.feed.feed
import ru.kode.way.dslparity.full.DslParityFullSchema
import ru.kode.way.dslparity.full.FullNodeBuilder
import ru.kode.way.dslparity.full.PromoNodeBuilder
import ru.kode.way.dslparity.full.full
import ru.kode.way.dslparity.history.DslParityHistorySchema
import ru.kode.way.dslparity.history.HistNodeBuilder
import ru.kode.way.dslparity.history.ShopNodeBuilder
import ru.kode.way.dslparity.history.hist
import ru.kode.way.dslparity.history.shop
import ru.kode.way.dslparity.home.DslParityHomeSchema
import ru.kode.way.dslparity.home.HomeNodeBuilder
import ru.kode.way.dslparity.home.home
import ru.kode.way.dslparity.login.DslParityLoginSchema
import ru.kode.way.dslparity.login.LoginNodeBuilder
import ru.kode.way.dslparity.login.login
import ru.kode.way.dslparity.nested.DslParityNestedSchema
import ru.kode.way.dslparity.nested.InnerNodeBuilder
import ru.kode.way.dslparity.nested.InnerSchema
import ru.kode.way.dslparity.nested.LeftNodeBuilder
import ru.kode.way.dslparity.nested.LeftSchema
import ru.kode.way.dslparity.nested.MainNodeBuilder
import ru.kode.way.dslparity.nested.RightNodeBuilder
import ru.kode.way.dslparity.nested.RightSchema
import ru.kode.way.dslparity.nested.TabsSchema
import ru.kode.way.dslparity.nested.left
import ru.kode.way.dslparity.nested.main
import ru.kode.way.dslparity.nested.right
import ru.kode.way.dslparity.params.DslParityParamsSchema
import ru.kode.way.dslparity.params.ParamsChildFinishRequest
import ru.kode.way.dslparity.params.ParamsNodeBuilder
import ru.kode.way.dslparity.params.params
import ru.kode.way.dslparity.perm.DslParityPermSchema
import ru.kode.way.dslparity.perm.PermNodeBuilder
import ru.kode.way.dslparity.perm.perm
import ru.kode.way.dslparity.settings
import ru.kode.way.dslparity.tabs.DslParityTabsSchema
import ru.kode.way.dslparity.tabs.TabsChildFinishRequest
import ru.kode.way.dslparity.tabs.TabsNodeBuilder
import ru.kode.way.tr
import ru.kode.way.dslparity.full.ShopNodeBuilder as FullShopNodeBuilder
import ru.kode.way.dslparity.full.promo as fullPromo
import ru.kode.way.dslparity.full.shop as fullShop
import ru.kode.way.dslparity.nested.TabsNodeBuilder as NestedTabsNodeBuilder

/** The DSL counterparts of `dsl-parity-*.dot`. */
private object LoginGraph : Graph("DslLogin") {
  val login = parameterizedFlow<String, Int>("login")
  val credentials = screen("credentials")
  val otp = parameterizedScreen<Int>("otp")

  init {
    login to credentials to otp
  }
}

private object AppGraph : Graph("DslApp") {
  val app = parameterizedFlow<Int, Unit>("app")
  val intro = screen("intro")
  val details = parameterizedScreen<String?>("details")
  val settings = flow<String>("settings")
  val general = screen("general")
  val login = include("login", LoginGraph.login)

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
  val tabs = parallel<Unit>("tabs")
  val home = include("home", HomeGraph.home)
  val feed = include("feed", FeedGraph.feed)

  init {
    tabs to home
    tabs to feed
  }
}

private object CounterGraph : Graph("DslCounter") {
  val counter = parameterizedFlow<Int?, Unit>("counter")
  val counterScreen = screen("counterScreen")

  init {
    counter to counterScreen
  }
}

private object ParamsGraph : Graph("DslParams") {
  val params = parameterizedFlow<String?, Unit>("params")
  val paramsScreen = screen("paramsScreen")
  val counter = include("counter", CounterGraph.counter)

  init {
    params to paramsScreen to counter
  }
}

private object HistoryGraph : Graph("DslHistory") {
  val hist = flow<Unit>("hist")
  val home = screen("home")
  val shop = flow<Unit>("shop")
  val cart = screen("cart")
  val checkout = screen("checkout")
  val cartHistory = history("cartHistory")
  val cartDeepHistory = deepHistory("cartDeepHistory")

  init {
    hist to home
    hist to shop to cart to checkout
    shop to cartHistory
    shop to cartDeepHistory
  }
}

private object NestedGraph : Graph("DslNested") {
  val main = flow<Unit>("main")
  val start = screen("start")
  val tabs = parallel<Unit>("tabs")
  val left = flow<Unit>("left")
  val leftScreen = screen("leftScreen")
  val leftDetails = screen("leftDetails")
  val inner = parallel<Unit>("inner")
  val right = flow<Unit>("right")
  val rightScreen = screen("rightScreen")
  val feed = include("feed", FeedGraph.feed)
  val tabsHistory = deepHistory("tabsHistory")

  init {
    main to start
    main to tabs
    tabs to left to leftScreen to leftDetails
    left to inner
    inner to right to rightScreen
    inner to feed
    tabs to tabsHistory
  }
}

private object PermGraph : Graph("DslPerm") {
  val perm = parameterizedFlow<String, String>("perm")
  val permIntro = screen("permIntro")
  val permDetails = screen("permDetails")

  init {
    perm to permIntro to permDetails
  }
}

private object FanInGraph : Graph("DslFanIn") {
  val shared = flow<Unit>("shared")
  val a = parameterizedScreen<Int>("a")
  val b = parameterizedScreen<String>("b")
  val perm = include("perm", PermGraph.perm)

  init {
    shared to a to perm
    shared to b to perm
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

private fun loginNode(spec: AppSpec, payload: Any?) = TestFlowNodeWithResult(
  initialTarget = spec.credentials,
  dismissResult = 0,
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

      override fun createSettingsNodeBuilder(): NodeBuilder = SettingsNodeBuilder(
        object : SettingsNodeBuilder.Factory {
          override fun createRootNode() = settingsNode(spec)
          override fun createGeneralNode() = TestScreenNode()
        },
        schema,
      )

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

private object FullGraph : Graph("DslParityFull") {
  val full = flow<Unit>("full")
  val home = screen("home")
  val shop = parameterizedFlow<String, Unit>("shop")
  val shopScreen = screen("shopScreen")
  val item = parameterizedScreen<Int>("item")
  val promo = flow<Unit>("promo")
  val promoScreen = screen("promoScreen")

  init {
    full to home
    full to shop to shopScreen
    shop to item to promo to promoScreen
  }
}

private fun fullRootNode(home: Target, promo: Target) =
  TestFlowNode(initialTarget = home, transitions = listOf(tr("P", promo)))

private fun generatedFullBuilder(): NodeBuilder {
  val schema = DslParityFullSchema()
  return FullNodeBuilder(
    object : FullNodeBuilder.Factory {
      override fun createRootNode() = fullRootNode(Target.full.home, Target.full.promo("s1", 5))
      override fun createHomeNode() = TestScreenNode()
      override fun createShopNodeBuilder(shopId: String): NodeBuilder = FullShopNodeBuilder(
        object : FullShopNodeBuilder.Factory {
          override fun createRootNode(shopId: String) =
            TestFlowNode(initialTarget = Target.fullShop.shopScreen, payload = shopId)
          override fun createShopScreenNode() = TestScreenNode()
          override fun createItemNode(itemId: Int) = TestScreenNode(payload = itemId)
        },
        schema,
      )
      override fun createPromoNodeBuilder(): NodeBuilder = PromoNodeBuilder(
        object : PromoNodeBuilder.Factory {
          override fun createRootNode() = TestFlowNode(initialTarget = Target.fullPromo.promoScreen)
          override fun createPromoScreenNode() = TestScreenNode()
        },
        schema,
      )
    },
    schema,
  )
}

private fun dslFullBuilder(): NodeBuilder = FullGraph.nodeBuilder {
  val promo = FullGraph.promo.target(FullGraph.shop with "s1", FullGraph.item with 5)
  flow(FullGraph.full) { fullRootNode(FullGraph.home.target(), promo) }
  screen(FullGraph.home) { TestScreenNode() }
  flow(FullGraph.shop) { TestFlowNode(initialTarget = FullGraph.shopScreen.target(), payload = it) }
  screen(FullGraph.shopScreen) { TestScreenNode() }
  screen(FullGraph.item) { TestScreenNode(payload = it) }
  flow(FullGraph.promo) { TestFlowNode(initialTarget = FullGraph.promoScreen.target()) }
  screen(FullGraph.promoScreen) { TestScreenNode() }
}

private class ParamsSpec(
  val paramsScreen: Target,
  val counter: (Int?) -> Target,
  val counterScreen: Target,
  val isCounterFinished: (Event) -> Boolean,
)

private fun paramsRootNode(spec: ParamsSpec, payload: String?) = TestFlowNode(
  initialTarget = spec.paramsScreen,
  payload = payload,
  transitions = listOf(
    tr("C", spec.counter(null)),
    tr("C7", spec.counter(7)),
    TestFlowTransitionSpec(spec.isCounterFinished, NavigateTo(spec.paramsScreen)),
  ),
)

private fun counterNode(spec: ParamsSpec, payload: Int?) = TestFlowNode(
  initialTarget = spec.counterScreen,
  payload = payload,
  transitions = listOf(tr("F", Finish(Unit))),
)

private fun generatedParamsBuilder(): NodeBuilder {
  val spec = ParamsSpec(
    paramsScreen = Target.params.paramsScreen,
    counter = { Target.params.counter(it) },
    counterScreen = Target.counter.counterScreen,
    isCounterFinished = { it == ParamsChildFinishRequest.Counter },
  )
  return ParamsNodeBuilder(
    object : ParamsNodeBuilder.Factory {
      override fun createRootNode(name: String?) = paramsRootNode(spec, name)
      override fun createParamsScreenNode() = TestScreenNode()
      override fun createCounterNodeBuilder(start: Int?): NodeBuilder = CounterNodeBuilder(
        object : CounterNodeBuilder.Factory {
          override fun createRootNode(start: Int?) = counterNode(spec, start)
          override fun createCounterScreenNode() = TestScreenNode()
        },
        DslParityCounterSchema(),
      )
    },
    DslParityParamsSchema(DslParityCounterSchema()),
  )
}

private fun dslParamsBuilder(): NodeBuilder {
  val spec = ParamsSpec(
    paramsScreen = ParamsGraph.paramsScreen.target(),
    counter = { ParamsGraph.counter.target(it) },
    counterScreen = CounterGraph.counterScreen.target(),
    isCounterFinished = { it.childFlowResult(ParamsGraph.counter) != null },
  )
  return ParamsGraph.nodeBuilder {
    flow(ParamsGraph.params) { paramsRootNode(spec, it) }
    screen(ParamsGraph.paramsScreen) { TestScreenNode() }
    include(ParamsGraph.counter) {
      CounterGraph.nodeBuilder {
        flow(CounterGraph.counter) { counterNode(spec, it) }
        screen(CounterGraph.counterScreen) { TestScreenNode() }
      }
    }
  }
}

private class HistorySpec(
  val home: Target,
  val shop: Target,
  val cart: Target,
  val checkout: Target,
  val cartHistory: Target,
  val cartDeepHistory: Target,
)

private fun histRootNode(spec: HistorySpec) = TestFlowNode(
  initialTarget = spec.home,
  transitions = listOf(
    tr("S", spec.shop),
    tr("H", spec.home),
    tr("R", spec.cartHistory),
    tr("D", spec.cartDeepHistory),
  ),
)

private fun shopNode(spec: HistorySpec) = TestFlowNode(
  initialTarget = spec.cart,
  transitions = listOf(tr("C", spec.checkout)),
)

private fun generatedHistoryBuilder(): NodeBuilder {
  val spec = HistorySpec(
    home = Target.hist.home,
    shop = Target.hist.shop,
    cart = Target.shop.cart,
    checkout = Target.shop.checkout,
    cartHistory = Target.shop.cartHistory,
    cartDeepHistory = Target.shop.cartDeepHistory,
  )
  val schema = DslParityHistorySchema()
  return HistNodeBuilder(
    object : HistNodeBuilder.Factory {
      override fun createRootNode() = histRootNode(spec)
      override fun createHomeNode() = TestScreenNode()

      override fun createShopNodeBuilder(): NodeBuilder = ShopNodeBuilder(
        object : ShopNodeBuilder.Factory {
          override fun createRootNode() = shopNode(spec)
          override fun createCartNode() = TestScreenNode()
          override fun createCheckoutNode() = TestScreenNode()
        },
        schema,
      )
    },
    schema,
  )
}

private fun dslHistoryBuilder(): NodeBuilder {
  val spec = HistorySpec(
    home = HistoryGraph.home.target(),
    shop = HistoryGraph.shop.target(),
    cart = HistoryGraph.cart.target(),
    checkout = HistoryGraph.checkout.target(),
    cartHistory = HistoryGraph.cartHistory.target(),
    cartDeepHistory = HistoryGraph.cartDeepHistory.target(),
  )
  return HistoryGraph.nodeBuilder {
    flow(HistoryGraph.hist) { histRootNode(spec) }
    screen(HistoryGraph.home) { TestScreenNode() }
    flow(HistoryGraph.shop) { shopNode(spec) }
    screen(HistoryGraph.cart) { TestScreenNode() }
    screen(HistoryGraph.checkout) { TestScreenNode() }
  }
}

private class NestedSpec(
  val start: Target,
  val tabs: Target,
  val tabsHistory: Target,
  val leftScreen: Target,
  val leftDetails: Target,
  val inner: Target,
  val rightScreen: Target,
  val feedScreen: Target,
)

private fun mainRootNode(spec: NestedSpec) = TestFlowNode(
  initialTarget = spec.start,
  transitions = listOf(tr("T", spec.tabs), tr("B", spec.start), tr("TH", spec.tabsHistory)),
)

private fun leftNode(spec: NestedSpec) = TestFlowNode(
  initialTarget = spec.leftScreen,
  transitions = listOf(tr("LD", spec.leftDetails), tr("I", spec.inner)),
)

private fun feedBuilder(feedScreen: Target): NodeBuilder = FeedNodeBuilder(
  object : FeedNodeBuilder.Factory {
    override fun createRootNode() = TestFlowNode(initialTarget = feedScreen)
    override fun createFeedScreenNode() = TestScreenNode()
  },
  DslParityFeedSchema(),
)

private fun generatedNestedBuilder(): NodeBuilder {
  val spec = NestedSpec(
    start = Target.main.start,
    tabs = Target.main.tabs,
    tabsHistory = Target.main.tabsHistory,
    leftScreen = Target.left.leftScreen,
    leftDetails = Target.left.leftDetails,
    inner = Target.main.inner,
    rightScreen = Target.right.rightScreen,
    feedScreen = Target.feed.feedScreen,
  )
  val innerBuilder = {
    InnerNodeBuilder(
      object : InnerNodeBuilder.Factory {
        override fun createRootNode() = TestParallelNode()
        override fun createFeedNodeBuilder() = feedBuilder(spec.feedScreen)
        override fun createRightNodeBuilder(): NodeBuilder = RightNodeBuilder(
          object : RightNodeBuilder.Factory {
            override fun createRootNode() = TestFlowNode(initialTarget = spec.rightScreen)
            override fun createRightScreenNode() = TestScreenNode()
          },
          RightSchema(),
        )
      },
      InnerSchema(DslParityFeedSchema()),
    )
  }
  return MainNodeBuilder(
    object : MainNodeBuilder.Factory {
      override fun createRootNode() = mainRootNode(spec)
      override fun createStartNode() = TestScreenNode()
      override fun createTabsNodeBuilder(): NodeBuilder = NestedTabsNodeBuilder(
        object : NestedTabsNodeBuilder.Factory {
          override fun createRootNode() = TestParallelNode()
          override fun createInnerNodeBuilder(): NodeBuilder = innerBuilder()
          override fun createLeftNodeBuilder(): NodeBuilder = LeftNodeBuilder(
            object : LeftNodeBuilder.Factory {
              override fun createRootNode() = leftNode(spec)
              override fun createInnerNodeBuilder(): NodeBuilder = innerBuilder()
              override fun createLeftScreenNode() = TestScreenNode()
              override fun createLeftDetailsNode() = TestScreenNode()
            },
            LeftSchema(DslParityFeedSchema()),
          )
        },
        TabsSchema(DslParityFeedSchema()),
      )
    },
    DslParityNestedSchema(DslParityFeedSchema()),
  )
}

private fun dslNestedBuilder(): NodeBuilder {
  val spec = NestedSpec(
    start = NestedGraph.start.target(),
    tabs = NestedGraph.tabs.target(),
    tabsHistory = NestedGraph.tabsHistory.target(),
    leftScreen = NestedGraph.leftScreen.target(),
    leftDetails = NestedGraph.leftDetails.target(),
    inner = NestedGraph.inner.target(),
    rightScreen = NestedGraph.rightScreen.target(),
    feedScreen = FeedGraph.feedScreen.target(),
  )
  return NestedGraph.nodeBuilder {
    flow(NestedGraph.main) { mainRootNode(spec) }
    screen(NestedGraph.start) { TestScreenNode() }
    parallel(NestedGraph.tabs) { TestParallelNode() }
    flow(NestedGraph.left) { leftNode(spec) }
    screen(NestedGraph.leftScreen) { TestScreenNode() }
    screen(NestedGraph.leftDetails) { TestScreenNode() }
    parallel(NestedGraph.inner) { TestParallelNode() }
    flow(NestedGraph.right) { TestFlowNode(initialTarget = spec.rightScreen) }
    screen(NestedGraph.rightScreen) { TestScreenNode() }
    include(NestedGraph.feed) {
      FeedGraph.nodeBuilder {
        flow(FeedGraph.feed) { TestFlowNode(initialTarget = spec.feedScreen) }
        screen(FeedGraph.feedScreen) { TestScreenNode() }
      }
    }
  }
}

private class FanInSpec(
  val a: (Int) -> Target,
  val b: (String) -> Target,
  val permViaA: (String) -> Target,
  val permViaB: (String) -> Target,
  val permDetails: Target,
  val permIntro: Target,
  val permResult: (Event) -> String?,
)

private fun sharedRootNode(spec: FanInSpec) = TestFlowNode(
  initialTarget = spec.a(1),
  transitions = listOf(
    tr("PA", spec.permViaA("x")),
    tr("PB", spec.permViaB("y")),
    TestFlowTransitionSpec({ spec.permResult(it) != null }, NavigateTo(spec.b("after"))),
  ),
)

private fun permNode(spec: FanInSpec, reason: String) = TestFlowNodeWithResult(
  initialTarget = spec.permIntro,
  dismissResult = "dismissed",
  payload = reason,
  transitions = listOf(tr("PD", spec.permDetails), tr("F", Finish("granted"))),
)

private fun generatedFanInBuilder(): NodeBuilder {
  val spec = FanInSpec(
    a = { Target.shared.a(it) },
    b = { Target.shared.b(it) },
    permViaA = { Target.shared.permViaA(it) },
    permViaB = { Target.shared.permViaB(it) },
    permIntro = Target.perm.permIntro,
    permDetails = Target.perm.permDetails,
    permResult = { (it as? SharedChildFinishRequest.Perm)?.result },
  )
  return SharedNodeBuilder(
    object : SharedNodeBuilder.Factory {
      override fun createRootNode() = sharedRootNode(spec)
      override fun createANode(aId: Int) = TestScreenNode(payload = aId)
      override fun createBNode(bId: String) = TestScreenNode(payload = bId)
      override fun createPermNodeBuilder(reason: String): NodeBuilder = PermNodeBuilder(
        object : PermNodeBuilder.Factory {
          override fun createRootNode(reason: String) = permNode(spec, reason)
          override fun createPermIntroNode() = TestScreenNode()
          override fun createPermDetailsNode() = TestScreenNode()
        },
        DslParityPermSchema(),
      )
    },
    DslParityFanInSchema(DslParityPermSchema()),
  )
}

private fun dslFanInBuilder(): NodeBuilder {
  val spec = FanInSpec(
    a = { FanInGraph.a.target(it) },
    b = { FanInGraph.b.target(it) },
    permViaA = { FanInGraph.perm.target(it, via = FanInGraph.a) },
    permViaB = { FanInGraph.perm.target(it, via = FanInGraph.b) },
    permIntro = PermGraph.permIntro.target(),
    permDetails = PermGraph.permDetails.target(),
    permResult = { it.childFlowResult(FanInGraph.perm) },
  )
  return FanInGraph.nodeBuilder {
    flow(FanInGraph.shared) { sharedRootNode(spec) }
    screen(FanInGraph.a) { TestScreenNode(payload = it) }
    screen(FanInGraph.b) { TestScreenNode(payload = it) }
    include(FanInGraph.perm) {
      PermGraph.nodeBuilder {
        flow(PermGraph.perm) { permNode(spec, it) }
        screen(PermGraph.permIntro) { TestScreenNode() }
        screen(PermGraph.permDetails) { TestScreenNode() }
      }
    }
  }
}

/** Everything a test can observe after each step: per region the active path, the alive paths and their payloads. */
private fun NavigationState.describe(): List<String> = regions.values.map { region ->
  val payloads = region.alive.joinToString { path ->
    val payload = when (val node = region.nodes[path]) {
      is TestFlowNode -> node.payload
      is TestFlowNodeWithResult<*> -> node.payload
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
    should("pass typed and nullable payloads as the generated graph does") {
      val events = listOf("C", "F", "C7", "F")
      for (rootPayload in listOf(null, "bob")) {
        val (generated, _) = run(generatedParamsBuilder(), rootPayload, events)
        val (dsl, _) = run(dslParamsBuilder(), rootPayload, events)

        dsl shouldBe generated
        dsl.map { it.single().substringBefore(' ') } shouldContainExactly listOf(
          "params.paramsScreen",
          "params.paramsScreen.counter.counterScreen",
          "params.paramsScreen.counter.counterScreen",
          "params.paramsScreen",
          "params.paramsScreen.counter.counterScreen",
          "params.paramsScreen.counter.counterScreen",
          "params.paramsScreen",
        )
        dsl[1].single() shouldContain "params=$rootPayload, params.paramsScreen=null, params.paramsScreen.counter=null,"
        dsl[4].single() shouldContain "params.paramsScreen.counter=7,"
      }
    }

    should("rebuild ancestors which are not alive from a full target as the generated graph does") {
      val (generated, _) = run(generatedFullBuilder(), rootPayload = null, listOf("P"))
      val (dsl, _) = run(dslFullBuilder(), rootPayload = null, listOf("P"))

      dsl shouldBe generated
      dsl.last().single() shouldBe
        "full.shop.item.promo.promoScreen [full=null, full.shop=s1, full.shop.item=5, full.shop.item.promo=null, " +
        "full.shop.item.promo.promoScreen=null]"
      shouldThrow<IllegalArgumentException> { FullGraph.shopScreen.target(FullGraph.item with 5) }.message shouldBe
        "'item' is not a parameterized ancestor of 'shopScreen'"
      shouldThrow<IllegalArgumentException> { FullGraph.item.target(5, FullGraph.item with 5) }.message shouldBe
        "'item' is not a parameterized ancestor of 'item'"
    }

    should("navigate to history nodes as the generated graph does") {
      val events = listOf("S", "C", "H", "D", "H", "R")

      val (generated, _) = run(generatedHistoryBuilder(), rootPayload = null, events)
      val (dsl, _) = run(dslHistoryBuilder(), rootPayload = null, events)

      dsl shouldBe generated
      // the deep history restores checkout, the shallow one only the direct child of shop
      dsl.map { it.single().substringBefore(' ') } shouldContainExactly listOf(
        "hist.home",
        "hist.shop.cart",
        "hist.shop.cart.checkout",
        "hist.home",
        "hist.shop.cart.checkout",
        "hist.home",
        "hist.shop.cart",
      )
    }

    should("navigate nested parallel flows as the generated graph does") {
      val events = listOf("T", "LD", "I", "B")

      val (generated, _) = run(generatedNestedBuilder(), rootPayload = null, events)
      val (dsl, _) = run(dslNestedBuilder(), rootPayload = null, events)

      dsl shouldBe generated
      dsl[3].map { it.substringBefore(' ') } shouldContainExactly listOf(
        "main.tabs",
        "main.tabs.left.inner",
        "main.tabs.left.inner.right.rightScreen",
        "main.tabs.left.inner.feed.feedScreen",
      )
      dsl[4].single() shouldStartWith "main.start "
      // the deep history of the outer parallel flow restores its regions, the nested ones included
      val (generatedWithHistory, _) = run(generatedNestedBuilder(), rootPayload = null, events + "TH")
      val (withHistory, _) = run(dslNestedBuilder(), rootPayload = null, events + "TH")
      withHistory shouldBe generatedWithHistory
      withHistory.last() shouldBe dsl[3]
    }

    should("enter an included graph from each of its parents as the generated graph does") {
      val events = listOf("PA", "PD", "F", "PB", "F")

      val (generated, _) = run(generatedFanInBuilder(), rootPayload = null, events)
      val (dsl, _) = run(dslFanInBuilder(), rootPayload = null, events)

      dsl shouldBe generated
      dsl.map { it.single().substringBefore(' ') } shouldContainExactly listOf(
        "shared.a",
        "shared.a.perm.permIntro",
        "shared.a.perm.permIntro.permDetails",
        "shared.a.perm.permIntro.permDetails",
        "shared.b",
        "shared.b.perm.permIntro",
        "shared.b.perm.permIntro",
        "shared.b",
      )
      dsl[1].single() shouldContain "shared.a.perm=x,"
      dsl[5].single() shouldContain "shared.b=after, shared.b.perm=y,"
    }
  })
