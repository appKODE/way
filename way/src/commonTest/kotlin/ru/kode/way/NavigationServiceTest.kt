package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import ru.kode.way.nav01.NavService01Schema
import ru.kode.way.nav02.NavService02Schema
import ru.kode.way.nav04.NavService04Schema
import ru.kode.way.nav05.NavService05Schema
import ru.kode.way.nav06.NavService06Schema
import ru.kode.way.nav07.NavService07Schema
import ru.kode.way.nav08.NavService08Schema
import ru.kode.way.nav09.NavService09ParentSchema
import ru.kode.way.nav09.child.NavService09ChildSchema
import ru.kode.way.nav09.child.PermissionsNodeBuilder
import ru.kode.way.nav10.NavService10LoginSchema
import ru.kode.way.nav10.NavService10PermissionsSchema
import ru.kode.way.nav10.NavService10Schema
import ru.kode.way.nav11.NavService11Schema
import ru.kode.way.nav12.AppNodeBuilder
import ru.kode.way.nav12.LoginNodeBuilder
import ru.kode.way.nav12.NavService12LoginSchema
import ru.kode.way.nav12.NavService12Schema
import java.nio.charset.Charset
import ru.kode.way.nav01.app as app01
import ru.kode.way.nav02.app as app02
import ru.kode.way.nav02.permissions as permissions02
import ru.kode.way.nav04.app as app04
import ru.kode.way.nav04.permissions as permissions04
import ru.kode.way.nav04.profile as profile04
import ru.kode.way.nav05.app as app05
import ru.kode.way.nav06.app as app06
import ru.kode.way.nav07.app as app07
import ru.kode.way.nav07.login as login07
import ru.kode.way.nav07.onboarding as onboarding07
import ru.kode.way.nav08.app as app08
import ru.kode.way.nav08.login as login08
import ru.kode.way.nav08.onboarding as onboarding08
import ru.kode.way.nav09.app as app09
import ru.kode.way.nav09.child.permissions as permissions09
import ru.kode.way.nav10.app as app10
import ru.kode.way.nav10.login as login10
import ru.kode.way.nav10.permissions as permissions10
import ru.kode.way.nav11.app as app11
import ru.kode.way.nav11.login as login11
import ru.kode.way.nav12.app as app12
import ru.kode.way.nav12.login as login12

class NavigationServiceTest : ShouldSpec({
  should("switch to direct initial state") {
    val sut = NavigationService(
      NavService01Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(initialTarget = Target.app01.intro),
          "app.intro" to TestScreenNode(),
        )
      ),
      onFinish = { _: Int -> Stay },
    )

    sut.collectTransitions().test {
      awaitItem().active shouldBe "app.intro"
    }
  }

  should("switch to initial state requiring sub-flow transition") {
    val sut = NavigationService(
      NavService02Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app02.permissions { Finish(Unit) }
          ),
          "app.permissions" to TestFlowNode(
            initialTarget = Target.permissions02.intro
          ),
          "app.permissions.intro" to TestScreenNode()
        )
      ),
      onFinish = { _: Unit -> Stay },
    )

    sut.collectTransitions().test {
      awaitItem().active shouldBe "app.permissions.intro"
    }
  }

  // TODO re-enable and adapt when compound-schema import will be done
  //   currently this is impossible because we can't have onboarding as a child **flow**-node of login,
  //   because they are described in a single dot file (enforced by NOTE_GROUPING_NODES_BY_FLOW_RULE)
  //   Once schema composition will be available login + onboarding can be extracted into a different file
  //   and then this test can be performed
//  should("switch to initial state creating all nested child screen nodes") {
//    val sut = NavigationService(
//      NavService03Schema(),
//      TestNodeBuilder(
//        mapOf(
//          "app" to TestFlowNode(
//            initialTarget = Target.app03.login { Finish(Unit) }
//          ),
//          "app.login" to TestFlowNode(
//            initialTarget = Target.login03.onboarding { Finish(Unit) }
//          ),
//          "app.login.onboarding" to TestFlowNode(
//            initialTarget = Target.onboarding03.intro
//          ),
//          "app.login.onboarding.intro" to TestScreenNode()
//        )
//      ),
//    )
//
//    sut.collectTransitions().test {
//      awaitItem().active shouldBe "app.login.onboarding.intro"
//    }
//  }

  should("ignore event completely if no node defines an actionable transition") {
    val sut = NavigationService(
      NavService02Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app02.permissions { Finish(Unit) }
          ),
          "app.permissions" to TestFlowNode(
            initialTarget = Target.permissions02.intro,
            transitions = listOf(
              tr(on = "A", target = Target.permissions02.intro)
            )
          ),
          "app.permissions.intro" to TestScreenNode()
        )
      ),
      onFinish = { _: Unit -> Stay },
    )

    sut.collectTransitions().test {
      awaitItem()

      sut.sendEvent(TestEvent("B"))

      // nothing should happen
      awaitItem().active shouldBe "app.permissions.intro"
    }
  }

  should("process events in a bottom-up order") {
    val sut = NavigationService(
      NavService04Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app04.permissions { Finish(Unit) },
            transitions = listOf(
              tr(on = "C", target = Target.app04.profile { Ignore })
            )
          ),
          "app.permissions" to TestFlowNode(
            initialTarget = Target.permissions04.intro,
            transitions = listOf(
              tr(on = "B", target = Target.permissions04.intro)
            )
          ),
          "app.permissions.intro" to TestScreenNode(
            transitions = listOf(
              trs(on = "A", target = Target.permissions04.request)
            )
          ),
          "app.permissions.request" to TestScreenNode(),
          "app.profile" to TestFlowNode(
            initialTarget = Target.profile04.main,
          ),
          "app.profile.main" to TestScreenNode()
        )
      ),
      onFinish = { _: Unit -> Stay },
    )

    sut.collectTransitions().test {
      awaitItem()

      sut.sendEvent(TestEvent("A"))
      awaitItem().active shouldBe "app.permissions.request"

      sut.sendEvent(TestEvent("B"))
      awaitItem().active shouldBe "app.permissions.intro"

      sut.sendEvent(TestEvent("C"))
      awaitItem().active shouldBe "app.profile.main"
    }
  }

  should("replace nodes when transitioning between sibling nodes") {
    val sut = NavigationService(
      NavService05Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app05.intro,
            transitions = listOf(
              tr(on = "A", target = Target.app05.main),
              tr(on = "B", target = Target.app05.test),
              tr(on = "C", target = Target.app05.main),
            )
          ),
          "app.intro" to TestScreenNode(),
          "app.main" to TestScreenNode(),
          "app.test" to TestScreenNode(),
        )
      ),
      onFinish = { _: Unit -> Stay },
    )

    sut.collectTransitions().test {
      awaitItem().apply {
        alive.shouldContainInOrder("app", "app.intro")
        active shouldBe "app.intro"
      }

      sut.sendEvent(TestEvent("A"))
      awaitItem().apply {
        alive.shouldContainInOrder("app", "app.main")
        active shouldBe "app.main"
      }
      sut.sendEvent(TestEvent("B"))
      awaitItem().apply {
        alive.shouldContainInOrder("app", "app.test")
        active shouldBe "app.test"
      }

      sut.sendEvent(TestEvent("C"))
      awaitItem().apply {
        alive.shouldContainInOrder("app", "app.main")
        active shouldBe "app.main"
      }
    }
  }

  should("append to live nodes when transitioning to child screen node sequentially") {
    val sut = NavigationService(
      NavService06Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app06.intro,
            transitions = listOf(
              tr(on = "A", target = Target.app06.main),
              tr(on = "B", target = Target.app06.test),
            )
          ),
          "app.intro" to TestScreenNode(),
          "app.intro.main" to TestScreenNode(),
          "app.intro.main.test" to TestScreenNode(),
        )
      ),
      onFinish = { _: Unit -> Stay },
    )

    sut.collectTransitions().test {
      awaitItem().apply {
        alive.shouldContainInOrder("app", "app.intro")
        active shouldBe "app.intro"
      }

      sut.sendEvent(TestEvent("A"))
      awaitItem().apply {
        alive.shouldContainInOrder("app", "app.intro", "app.intro.main")
        active shouldBe "app.intro.main"
      }

      sut.sendEvent(TestEvent("B"))
      awaitItem().apply {
        alive.shouldContainInOrder("app", "app.intro", "app.intro.main", "app.intro.main.test")
        active shouldBe "app.intro.main.test"
      }
    }
  }

  should("append to live nodes when transitioning to grand child screen node") {
    val sut = NavigationService(
      NavService06Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app06.intro,
            transitions = listOf(
              tr(on = "A", target = Target.app06.test),
            )
          ),
          "app.intro" to TestScreenNode(),
          "app.intro.main" to TestScreenNode(),
          "app.intro.main.test" to TestScreenNode(),
        )
      ),
      onFinish = { _: Unit -> Stay },
    )

    sut.collectTransitions().test {
      awaitItem().apply {
        alive.shouldContainInOrder("app", "app.intro")
        active shouldBe "app.intro"
      }

      sut.sendEvent(TestEvent("A"))
      awaitItem().apply {
        alive.shouldContainInOrder("app", "app.intro", "app.intro.main", "app.intro.main.test")
        active shouldBe "app.intro.main.test"
      }
    }
  }

  should("call onFinish to inform parent flow of result") {
    val sut = NavigationService(
      NavService07Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app07.onboarding { r: Int ->
              if (r == 42) {
                NavigateTo(Target.app07.login { Finish(33.0) })
              } else {
                Finish(30.0)
              }
            }
          ),
          "app.onboarding" to TestFlowNodeWithResult(
            initialTarget = Target.onboarding07.intro,
            dismissResult = 33,
            transitions = listOf(
              tr(on = "A", Finish(42))
            )
          ),
          "app.onboarding.intro" to TestScreenNode(),
          "app.login" to TestFlowNode(
            initialTarget = Target.login07.credentials,
          ),
          "app.login.credentials" to TestScreenNode()
        )
      ),
      onFinish = { _: Double -> Stay },
    )

    sut.collectTransitions().test {
      awaitItem()

      sut.sendEvent(TestEvent("A"))

      awaitItem().active shouldBe "app.login.credentials"
    }
  }

  should("call onFinish to inform parent flow of result from non-initial node in the sub-flow") {
    val sut = NavigationService(
      NavService08Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app08.onboarding { r: Int ->
              if (r == 42) {
                NavigateTo(Target.app08.login { Finish(Unit) })
              } else {
                Finish(Unit)
              }
            }
          ),
          "app.onboarding" to TestFlowNodeWithResult(
            initialTarget = Target.onboarding08.intro,
            dismissResult = 33,
            transitions = listOf(
              tr(on = "A", target = Target.onboarding08.page1),
              tr(on = "B", Finish(42))
            )
          ),
          "app.onboarding.intro" to TestScreenNode(),
          "app.onboarding.page1" to TestScreenNode(),
          "app.login" to TestFlowNode(
            initialTarget = Target.login08.credentials,
          ),
          "app.login.credentials" to TestScreenNode()
        )
      ),
      onFinish = { _: Unit -> Stay },
    )

    sut.collectTransitions().test {
      awaitItem()

      sut.sendEvent(TestEvent("A"))
      awaitItem().active shouldBe "app.onboarding.page1"

      sut.sendEvent(TestEvent("B"))
      awaitItem().active shouldBe "app.login.credentials"
    }
  }

  should("correctly call onFinish if parent flow finishes as a result of a child flow finish") {
    val sut = NavigationService(
      NavService10Schema(NavService10LoginSchema(NavService10PermissionsSchema())),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app10.page1,
            transitions = listOf(
              tr("A", Target.app10.login { _: Int -> NavigateTo(Target.app10.page1) })
            )
          ),
          "app.page1" to TestScreenNode(),
          "app.page1.login" to TestFlowNodeWithResult(
            initialTarget = Target.login10.credentials,
            dismissResult = 33,
            transitions = listOf(
              tr(on = "A", target = Target.login10.permissions { result: String -> Finish(result.toInt()) }),
            )
          ),
          "app.page1.login.credentials" to TestScreenNode(),
          "app.page1.login.credentials.permissions" to TestFlowNode(
            initialTarget = Target.permissions10.intro,
            transitions = listOf(
              tr(on = "B", Finish("42")),
            )
          ),
          "app.page1.login.credentials.permissions.intro" to TestScreenNode(),
        )
      ),
      onFinish = { _: Unit -> Stay },
    )

    sut.collectTransitions().test {
      awaitItem()

      sut.sendEvent(TestEvent("A"))
      awaitItem().active shouldBe "app.page1.login.credentials"

      sut.sendEvent(TestEvent("A"))
      awaitItem().active shouldBe "app.page1.login.credentials.permissions.intro"

      sut.sendEvent(TestEvent("B"))
      awaitItem().active shouldBe "app.page1"
    }
  }

  should("stay on current child node when Stay transition is returned") {
    val sut = NavigationService(
      NavService06Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app06.intro,
            transitions = listOf(
              tr(on = "A", Target.app06.test),
              tr(on = "B", Stay),
            )
          ),
          "app.intro" to TestScreenNode(),
          "app.intro.main" to TestScreenNode(),
          "app.intro.main.test" to TestScreenNode(),
        )
      ),
      onFinish = { _: Unit -> Stay },
    )

    sut.collectTransitions().test {
      awaitItem()
      sut.sendEvent(TestEvent("A"))
      awaitItem().apply {
        active shouldBe "app.intro.main.test"
      }
      sut.sendEvent(TestEvent("B"))
      awaitItem().apply {
        active shouldBe "app.intro.main.test"
      }
    }
  }

  should("correctly navigate when using imported schemas") {
    val sut = NavigationService(
      NavService09ParentSchema(permissionsSchema = NavService09ChildSchema()),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app09.page1,
            transitions = listOf(
              tr(on = "A", Target.app09.permissions { Ignore }),
            )
          ),
          "app.page1" to TestScreenNode(),
          "app.page1.permissions" to TestFlowNode(
            initialTarget = Target.permissions09.intro,
            transitions = listOf(
              tr(on = "A", Target.permissions09.request),
            )
          ),
          "app.page1.permissions.intro" to TestScreenNode(),
          "app.page1.permissions.intro.request" to TestScreenNode(),
        )
      ),
      onFinish = { _: Unit -> Stay },
    )

    sut.collectTransitions().test {
      awaitItem()
      sut.sendEvent(TestEvent("A"))
      awaitItem().apply {
        active shouldBe "app.page1.permissions.intro"
      }
      sut.sendEvent(TestEvent("A"))
      awaitItem().apply {
        active shouldBe "app.page1.permissions.intro.request"
      }
    }
  }

  should("correctly handle back navigation") {
    var isFinished = false
    val sut = NavigationService(
      NavService11Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app11.login(onFinish = { NavigateTo(Target.app11.main) }),
          ),
          "app.intro" to TestScreenNode(),
          "app.intro.main" to TestScreenNode(),
          "app.intro.main.test" to TestScreenNode(),
          "app.intro.main.test.login" to TestFlowNode(
            initialTarget = Target.login11.otp,
          ),
          "app.intro.main.test.login.credentials" to TestScreenNode(),
          "app.intro.main.test.login.credentials.otp" to TestScreenNode(),
        )
      ),
      onFinish = { _: Unit -> isFinished = true; Ignore },
    )

    sut.collectTransitions().test {
      awaitItem().apply {
        active shouldBe "app.intro.main.test.login.credentials.otp"
      }

      sut.sendEvent(Event.Back)
      awaitItem().apply {
        active shouldBe "app.intro.main.test.login.credentials"
      }

      sut.sendEvent(Event.Back)
      awaitItem().apply {
        active shouldBe "app.intro.main"
      }

      sut.sendEvent(Event.Back)
      awaitItem().apply {
        active shouldBe "app.intro"
      }

      sut.sendEvent(Event.Back)
      awaitItem().apply {
        isFinished shouldBe true
        active shouldBe "app.intro"
      }
    }
  }

  should("use service.onFinish when receiving finish event") {
    var isFinished = false
    val sut = NavigationService(
      NavService01Schema(),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app01.intro,
            transitions = listOf(
              tr("A", Finish(33))
            )
          ),
          "app.intro" to TestScreenNode(),
        )
      ),
      onFinish = { result: Int ->
        if (result == 33) {
          isFinished = true
        }
        Ignore
      },
    )

    sut.collectTransitions().test {
      awaitItem().active shouldBe "app.intro"

      sut.sendEvent(TestEvent("A"))
      awaitItem().active shouldBe "app.intro"
      isFinished shouldBe true
    }
  }

  should("pass target arguments to flow, screen and sub-flow nodes") {
    val schema = NavService12Schema(NavService12LoginSchema())
    val nodeBuilder = AppNodeBuilder(
      object : AppNodeBuilder.Factory {
        override fun createRootNode(timeout: Int) = TestFlowNode(
          initialTarget = Target.app12.page1(Charsets.UTF_32),
          payload = timeout,
          transitions = listOf(
            tr("A", Target.app12.login(defaultUserName = "Dima", onFinish = { Stay }))
          )
        )

        override fun createPage2Node() = TestScreenNode()
        override fun createPage1Node(charset: Charset) = TestScreenNode(payload = charset)

        override fun createLoginNodeBuilder(defaultUserName: String): NodeBuilder {
          return LoginNodeBuilder(
            object : LoginNodeBuilder.Factory {
              override fun createRootNode(defaultUserName: String) = TestFlowNode(
                initialTarget = Target.login12.credentials(defaultPhone = "+7981123456"),
                payload = defaultUserName,
                transitions = listOf(
                  tr("B", Target.login12.otp(useAnimation = true))
                )
              )

              override fun createCredentialsNode(defaultPhone: String) = TestScreenNode(payload = defaultPhone)
              override fun createOtpNode(useAnimation: Boolean) = TestScreenNode(payload = useAnimation)
            },
            NavService12LoginSchema()
          )
        }
      },
      schema
    )

    val sut = NavigationService(
      schema,
      nodeBuilder,
      onFinish = { _: Int -> Ignore },
    )

    sut.collectTransitions(rootNodePayload = 42).test {
      awaitItem().apply {
        // root flow node should receive an argument
        (aliveNodes["app"] as TestFlowNode?)?.payload shouldBe 42
        // initial node of root flow should receive an argument
        (aliveNodes["app.page1"] as TestScreenNode?)?.payload shouldBe Charsets.UTF_32
      }

      sut.sendEvent(TestEvent("A"))

      awaitItem().apply {
        // sub flow node should receive an argument
        (aliveNodes["app.page1.login"] as TestFlowNode?)?.payload shouldBe "Dima"
        // initial node of sub flow should receive an argument
        (aliveNodes["app.page1.login.credentials"] as TestScreenNode?)?.payload shouldBe "+7981123456"
      }

      sut.sendEvent(TestEvent("B"))

      awaitItem().apply {
        // screen node should receive an argument
        (aliveNodes["app.page1.login.credentials.otp"] as TestScreenNode?)?.payload shouldBe true
      }
    }
  }

  should("correctly call onEntry/onExit in basic cases") {
    val entryCounts = mutableMapOf<String, Int>()
    val exitCounts = mutableMapOf<String, Int>()
    val sut = NavigationService(
      NavService10Schema(NavService10LoginSchema(NavService10PermissionsSchema())),
      TestNodeBuilder(
        mapOf(
          "app" to TestFlowNode(
            initialTarget = Target.app10.page1,
            onEntryImpl = { entryCounts["app"] = entryCounts["app"]?.let { it + 1 } ?: 1 },
            onExitImpl = { exitCounts["app"] = exitCounts["app"]?.let { it + 1 } ?: 1 },
            transitions = listOf(
              tr("A", Target.app10.login { _: Int -> NavigateTo(Target.app10.page1) })
            )
          ),
          "app.page1" to TestScreenNode(
            onEntryImpl = { entryCounts["app.page1"] = entryCounts["app.page1"]?.let { it + 1 } ?: 1 },
            onExitImpl = { exitCounts["app.page1"] = exitCounts["app.page1"]?.let { it + 1 } ?: 1 },
            transitions = listOf(
              trs("B", Target.app10.page2)
            )
          ),
          "app.page2" to TestScreenNode(
            onEntryImpl = { entryCounts["app.page2"] = entryCounts["app.page2"]?.let { it + 1 } ?: 1 },
            onExitImpl = { exitCounts["app.page2"] = exitCounts["app.page2"]?.let { it + 1 } ?: 1 },
            transitions = listOf(
              trs("A", Target.app10.login { _: Int -> NavigateTo(Target.app10.page1) })
            )
          ),
          "app.page1.login" to TestFlowNodeWithResult(
            initialTarget = Target.login10.credentials,
            dismissResult = 33,
            onEntryImpl = { entryCounts["app.page1.login"] = entryCounts["app.page1.login"]?.let { it + 1 } ?: 1 },
            onExitImpl = { exitCounts["app.page1.login"] = exitCounts["app.page1.login"]?.let { it + 1 } ?: 1 },
            transitions = listOf(
              tr(on = "A", target = Target.login10.permissions { result: String -> Finish(result.toInt()) }),
            )
          ),
          "app.page1.login.credentials" to TestScreenNode(
            onEntryImpl = {
              entryCounts["app.page1.login.credentials"] =
                entryCounts["app.page1.login.credentials"]?.let { it + 1 } ?: 1
            },
            onExitImpl = {
              exitCounts["app.page1.login.credentials"] =
                exitCounts["app.page1.login.credentials"]?.let { it + 1 } ?: 1
            },
          ),
          "app.page1.login.credentials.permissions" to TestFlowNode(
            initialTarget = Target.permissions10.intro,
            onEntryImpl = {
              entryCounts["app.page1.login.credentials.permissions"] =
                entryCounts["app.page1.login.credentials.permissions"]?.let { it + 1 } ?: 1
            },
            onExitImpl = {
              exitCounts["app.page1.login.credentials.permissions"] =
                exitCounts["app.page1.login.credentials.permissions"]?.let { it + 1 } ?: 1
            },
            transitions = listOf(
              tr(on = "B", Finish("42")),
            )
          ),
          "app.page1.login.credentials.permissions.intro" to TestScreenNode(
            onEntryImpl = {
              entryCounts["app.page1.login.credentials.permissions.intro"] =
                entryCounts["app.page1.login.credentials.permissions.intro"]?.let { it + 1 } ?: 1
            },
            onExitImpl = {
              exitCounts["app.page1.login.credentials.permissions.intro"] =
                exitCounts["app.page1.login.credentials.permissions.intro"]?.let { it + 1 } ?: 1
            },
          ),
        )
      ),
      onFinish = { _: Unit -> Stay },
    )

    entryCounts.shouldBeEmpty()
    exitCounts.shouldBeEmpty()

    sut.collectTransitions().test {
      awaitItem().active shouldBe "app.page1"
      entryCounts.shouldContainExactly(
        mapOf("app" to 1, "app.page1" to 1)
      )
      exitCounts.shouldBeEmpty()

      sut.sendEvent(TestEvent("A"))
      awaitItem().active shouldBe "app.page1.login.credentials"
      entryCounts.shouldContainExactly(
        mapOf(
          "app" to 1,
          "app.page1" to 1,
          "app.page1.login" to 1,
          "app.page1.login.credentials" to 1,
        )
      )
      exitCounts.shouldBeEmpty()

      sut.sendEvent(TestEvent("A"))
      awaitItem().active shouldBe "app.page1.login.credentials.permissions.intro"
      entryCounts.shouldContainExactly(
        mapOf(
          "app" to 1,
          "app.page1" to 1,
          "app.page1.login" to 1,
          "app.page1.login.credentials" to 1,
          "app.page1.login.credentials.permissions" to 1,
          "app.page1.login.credentials.permissions.intro" to 1,
        )
      )
      exitCounts.shouldBeEmpty()

      sut.sendEvent(TestEvent("B"))
      awaitItem().active shouldBe "app.page1"
      entryCounts.shouldContainExactly(
        mapOf(
          "app" to 1,
          "app.page1" to 1,
          "app.page1.login" to 1,
          "app.page1.login.credentials" to 1,
          "app.page1.login.credentials.permissions" to 1,
          "app.page1.login.credentials.permissions.intro" to 1,
        )
      )
      exitCounts.shouldContainExactly(
        mapOf(
          "app.page1.login.credentials.permissions.intro" to 1,
          "app.page1.login.credentials.permissions" to 1,
          "app.page1.login.credentials" to 1,
          "app.page1.login" to 1,
        )
      )

      sut.sendEvent(TestEvent("B"))
      awaitItem().active shouldBe "app.page2"
      entryCounts.shouldContainExactly(
        mapOf(
          "app" to 1,
          "app.page1" to 1,
          "app.page1.login" to 1,
          "app.page1.login.credentials" to 1,
          "app.page1.login.credentials.permissions" to 1,
          "app.page1.login.credentials.permissions.intro" to 1,
          "app.page2" to 1,
        )
      )
      exitCounts.shouldContainExactly(
        mapOf(
          "app.page1.login.credentials.permissions.intro" to 1,
          "app.page1.login.credentials.permissions" to 1,
          "app.page1.login.credentials" to 1,
          "app.page1.login" to 1,
          "app.page1" to 1,
        )
      )

      sut.sendEvent(TestEvent("A"))
      awaitItem().active shouldBe "app.page1.login.credentials"
      entryCounts.shouldContainExactly(
        mapOf(
          "app" to 1,
          "app.page1" to 2,
          "app.page1.login" to 2,
          "app.page1.login.credentials" to 2,
          "app.page1.login.credentials.permissions" to 1,
          "app.page1.login.credentials.permissions.intro" to 1,
          "app.page2" to 1,
        )
      )
      exitCounts.shouldContainExactly(
        mapOf(
          "app.page1.login.credentials.permissions.intro" to 1,
          "app.page1.login.credentials.permissions" to 1,
          "app.page1.login.credentials" to 1,
          "app.page1.login" to 1,
          "app.page1" to 1,
          "app.page2" to 1,
        )
      )
    }
  }

  // NOTE: For now this is an expected behavior: flow nodes can be built several times prior to being used
  // in transitions (for example during initial node resolution). Users are expected to use onEntry/onExit instead of
  // node constructors to initialize node-tree dependent data.
  // NOTE: In case the above restriction will be lifted and node construction will be guaranteed to happen once,
  // this test should be adjusted to test for exactly this case and this comment should be removed.
  should("call child flow builder twice ") {
    var createChildNodeCallCount = 0
    val sut = NavigationService<Unit>(
      NavService09ParentSchema(permissionsSchema = NavService09ChildSchema()),
      ru.kode.way.nav09.AppNodeBuilder(
        object : ru.kode.way.nav09.AppNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> {
            return TestFlowNode(initialTarget = Target.app09.permissions { Ignore })
          }

          override fun createPage1Node() = TestScreenNode()

          override fun createPermissionsNodeBuilder(): NodeBuilder {
            return PermissionsNodeBuilder(
              object : PermissionsNodeBuilder.Factory {
                override fun createRootNode(): FlowNode<*> {
                  createChildNodeCallCount += 1
                  return TestFlowNode(initialTarget = Target.permissions09.intro)
                }
                override fun createIntroNode() = TestScreenNode()
                override fun createRequestNode() = TestScreenNode()
              },
              NavService09ChildSchema()
            )
          }
        },
        NavService09ParentSchema(NavService09ChildSchema())
      ),
      onFinish = { Ignore }
    )

    sut.collectTransitions().test {
      awaitItem().apply {
        active shouldBe "app.page1.permissions.intro"
      }
      // See NOTEs above
      createChildNodeCallCount shouldBe 2
    }
  }

  should("cleanup nodes after flow finish") {
    val sut = NavigationService<Unit>(
      NavService09ParentSchema(permissionsSchema = NavService09ChildSchema()),
      ru.kode.way.nav09.AppNodeBuilder(
        object : ru.kode.way.nav09.AppNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> {
            return TestFlowNode(
              initialTarget = Target.app09.permissions { Ignore },
              transitions = listOf(
                tr(on = "A", NavigateTo(Target.app09.page1)),
                tr(on = "B", NavigateTo(Target.app09.permissions { Ignore })),
              )
            )
          }

          override fun createPage1Node() = TestScreenNode()

          override fun createPermissionsNodeBuilder(): NodeBuilder {
            return PermissionsNodeBuilder(
              object : PermissionsNodeBuilder.Factory {
                // This emulates flow node being cached by DI.
                // Generated node builders should cache their child node builders only while path is active, i.e.
                // AppNodeBuilder should cache PermissionsNodeBuilder only while "permissions" flow is active,
                // and then it should drop PermissionsNodeBuilder + its factory + this lazy node, and reconstruct
                // all this next time permission flow is started
                private val flowNode: FlowNode<*> by lazy {
                  TestFlowNode(
                    initialTarget = Target.permissions09.request,
                    transitions = listOf(
                      tr(on = "C", NavigateTo(Target.permissions09.intro)),
                      tr(on = "D", NavigateTo(Target.permissions09.request)),
                    )
                  )
                }
                override fun createRootNode(): FlowNode<*> {
                  return flowNode
                }
                override fun createIntroNode() = TestScreenNode()
                override fun createRequestNode() = TestScreenNode()
              },
              NavService09ChildSchema()
            )
          }
        },
        NavService09ParentSchema(NavService09ChildSchema())
      ),
      onFinish = { Ignore }
    )

    sut.collectTransitions().test {
      awaitItem().apply {
        active shouldBe "app.page1.permissions.intro.request"
        val permissionNodes = aliveNodes.filter { it.key.contains("permissions") }.values

        sut.sendEvent(TestEvent("A"))
        active shouldBe "app.page1"

        // PermissionsNodeBuilder should be released inside AppNodeBuilder at this point and on "B"-event it should
        // be reconstructed again

        sut.sendEvent(TestEvent("B"))
        active shouldBe "app.page1.permissions.intro.request"
        val newPermissionNodes = aliveNodes.filter { it.key.contains("permissions") }.values
        newPermissionNodes.shouldNotContainAnyOf(permissionNodes)
        val requestNode = aliveNodes.entries.find { it.key.endsWith("request") }!!.value

        sut.sendEvent(TestEvent("C"))
        sut.sendEvent(TestEvent("D"))
        val newRequestNode = aliveNodes.entries.find { it.key.endsWith("request") }!!.value

        requestNode shouldNotBe newRequestNode

        cancelAndIgnoreRemainingEvents()
      }
    }
  }
})
