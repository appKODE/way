package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.comparables.shouldBeLessThan
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
import ru.kode.way.par01.bottom.par01Bottom
import ru.kode.way.par01.par01App
import ru.kode.way.par01.top.par01Top
import ru.kode.way.par03.alpha.par03Alpha
import ru.kode.way.par03.beta.par03Beta
import ru.kode.way.par03.par03App
import java.nio.charset.Charset
import ru.kode.way.nav01.app as app01
import ru.kode.way.nav02.AppChildFinishRequest as Nav02AppChildFinishRequest
import ru.kode.way.nav02.app as app02
import ru.kode.way.nav02.permissions as permissions02
import ru.kode.way.nav04.AppChildFinishRequest as Nav04AppChildFinishRequest
import ru.kode.way.nav04.app as app04
import ru.kode.way.nav04.permissions as permissions04
import ru.kode.way.nav04.profile as profile04
import ru.kode.way.nav05.app as app05
import ru.kode.way.nav06.app as app06
import ru.kode.way.nav07.AppChildFinishRequest as Nav07AppChildFinishRequest
import ru.kode.way.nav07.app as app07
import ru.kode.way.nav07.login as login07
import ru.kode.way.nav07.onboarding as onboarding07
import ru.kode.way.nav08.AppChildFinishRequest as Nav08AppChildFinishRequest
import ru.kode.way.nav08.app as app08
import ru.kode.way.nav08.login as login08
import ru.kode.way.nav08.onboarding as onboarding08
import ru.kode.way.nav09.AppChildFinishRequest as Nav09AppChildFinishRequest
import ru.kode.way.nav09.app as app09
import ru.kode.way.nav09.child.permissions as permissions09
import ru.kode.way.nav10.AppChildFinishRequest as Nav10AppChildFinishRequest
import ru.kode.way.nav10.LoginChildFinishRequest as Nav10LoginChildFinishRequest
import ru.kode.way.nav10.app as app10
import ru.kode.way.nav10.login as login10
import ru.kode.way.nav10.permissions as permissions10
import ru.kode.way.nav11.AppChildFinishRequest as Nav11AppChildFinishRequest
import ru.kode.way.nav11.app as app11
import ru.kode.way.nav11.login as login11
import ru.kode.way.nav12.AppChildFinishRequest as Nav12AppChildFinishRequest
import ru.kode.way.nav12.app as app12
import ru.kode.way.nav12.login as login12

class NavigationServiceTest :
  ShouldSpec({
    should("switch to direct initial state") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )

      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.intro"
      }
    }

    should("switch to initial state requiring sub-flow transition") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService02Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app02.permissions,
              transitions = listOf(
                tr<Nav02AppChildFinishRequest.Permissions>(Finish(Unit)),
              ),
            ),
            "app.permissions" to TestFlowNode(
              initialTarget = Target.permissions02.intro,
            ),
            "app.permissions.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.permissions.intro"
      }
    }

    should("correctly post and process EnqueueEvent transitions") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(
                tr("A", EnqueueEvent(TestEvent("B"))),
                tr(on = "B", target = Target.app05.main),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      var enqueuedEvent: Event? = null

      sut.setEnqueuedEventsScheduler {
        enqueuedEvent = it
      }

      sut.collectTransitions()
        .test {
          awaitItem().active shouldBe "app.intro"
          sut.sendEvent(TestEvent("A"))
          awaitItem().active shouldBe "app.intro"
          enqueuedEvent?.also {
            sut.sendEvent(it)
            enqueuedEvent = null
          }
          awaitItem().active shouldBe "app.main"
          enqueuedEvent?.also {
            sut.sendEvent(it)
            enqueuedEvent = null
          }
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
        TestNodeBuilder(
          NavService02Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app02.permissions,
              transitions = listOf(
                tr<Nav02AppChildFinishRequest.Permissions>(Finish(Unit)),
              ),
            ),
            "app.permissions" to TestFlowNode(
              initialTarget = Target.permissions02.intro,
              transitions = listOf(
                tr(on = "A", target = Target.permissions02.intro),
              ),
            ),
            "app.permissions.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
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
        TestNodeBuilder(
          NavService04Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app04.permissions,
              transitions = listOf(
                tr(on = "C", target = Target.app04.profile),
                tr<Nav04AppChildFinishRequest.Permissions>(Finish(Unit)),
                tr<Nav04AppChildFinishRequest.Profile>(Ignore),
              ),
            ),
            "app.permissions" to TestFlowNode(
              initialTarget = Target.permissions04.intro,
              transitions = listOf(
                tr(on = "B", target = Target.permissions04.intro),
              ),
            ),
            "app.permissions.intro" to TestScreenNode(
              transitions = listOf(
                trs(on = "A", target = Target.permissions04.request),
              ),
            ),
            "app.permissions.request" to TestScreenNode(),
            "app.profile" to TestFlowNode(
              initialTarget = Target.profile04.main,
            ),
            "app.profile.main" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
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
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(
                tr(on = "A", target = Target.app05.main),
                tr(on = "B", target = Target.app05.test),
                tr(on = "C", target = Target.app05.main),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
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
        TestNodeBuilder(
          NavService06Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app06.intro,
              transitions = listOf(
                tr(on = "A", target = Target.app06.main),
                tr(on = "B", target = Target.app06.test),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.intro.main" to TestScreenNode(),
            "app.intro.main.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
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
        TestNodeBuilder(
          NavService06Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app06.intro,
              transitions = listOf(
                tr(on = "A", target = Target.app06.test),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.intro.main" to TestScreenNode(),
            "app.intro.main.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
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

    should("correctly handles parent/child finish requests") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService07Schema(),
          mapOf(
            "app" to object : FlowNode<Double> {
              override val initial = Target.app07.onboarding
              override val dismissResult = 0.0
              override fun transition(event: Event): FlowTransition<Double> = when (event) {
                is Nav07AppChildFinishRequest.Onboarding -> {
                  if (event.result == 42) {
                    NavigateTo(Target.app07.login)
                  } else {
                    Finish(30.0)
                  }
                }

                is Nav07AppChildFinishRequest.Login -> Finish(33.0)

                else -> Ignore
              }
            },
            "app.onboarding" to TestFlowNodeWithResult(
              initialTarget = Target.onboarding07.intro,
              dismissResult = 33,
              transitions = listOf(
                tr(on = "A", Finish(42)),
              ),
            ),
            "app.onboarding.intro" to TestScreenNode(),
            "app.login" to TestFlowNode(
              initialTarget = Target.login07.credentials,
            ),
            "app.login.credentials" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Double -> Stay },
      )

      sut.collectTransitions().test {
        awaitItem()

        sut.sendEvent(TestEvent("A"))

        awaitItem().active shouldBe "app.onboarding.intro" // after A, "finish" is sent
        awaitItem().active shouldBe "app.login.credentials" // after "finish"
      }
    }

    should("correctly handles parent/child finish requests from non-initial node") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService08Schema(),
          mapOf(
            "app" to object : FlowNode<Unit> {
              override val initial = Target.app08.onboarding
              override val dismissResult = Unit
              override fun transition(event: Event): FlowTransition<Unit> = when (event) {
                is Nav08AppChildFinishRequest.Onboarding -> {
                  if (event.result == 42) {
                    NavigateTo(Target.app08.login)
                  } else {
                    Finish(Unit)
                  }
                }

                is Nav08AppChildFinishRequest.Login -> Finish(Unit)

                else -> Ignore
              }
            },
            "app.onboarding" to TestFlowNodeWithResult(
              initialTarget = Target.onboarding08.intro,
              dismissResult = 33,
              transitions = listOf(
                tr(on = "A", target = Target.onboarding08.page1),
                tr(on = "B", Finish(42)),
              ),
            ),
            "app.onboarding.intro" to TestScreenNode(),
            "app.onboarding.page1" to TestScreenNode(),
            "app.login" to TestFlowNode(
              initialTarget = Target.login08.credentials,
            ),
            "app.login.credentials" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      sut.collectTransitions().test {
        awaitItem()

        sut.sendEvent(TestEvent("A"))
        awaitItem().active shouldBe "app.onboarding.page1"

        sut.sendEvent(TestEvent("B"))
        awaitItem().active shouldBe "app.onboarding.page1" // received B, sends "finish"
        awaitItem().active shouldBe "app.login.credentials" // after "finish"
      }
    }

    should("correctly handle finish if parent flow finishes as a result of a child flow finish") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService10Schema(NavService10LoginSchema(NavService10PermissionsSchema())),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app10.page1,
              transitions = listOf(
                tr("A", Target.app10.login),
                tr<Nav10AppChildFinishRequest.Login>(NavigateTo(Target.app10.page1)),
              ),
            ),
            "app.page1" to TestScreenNode(),
            "app.page1.login" to TestFlowNodeWithResult(
              initialTarget = Target.login10.credentials,
              dismissResult = 33,
              transitions = listOf(
                tr(on = "A", target = Target.login10.permissions),
                tr<Nav10LoginChildFinishRequest.Permissions>(Finish(44)),
              ),
            ),
            "app.page1.login.credentials" to TestScreenNode(),
            "app.page1.login.credentials.permissions" to TestFlowNode(
              initialTarget = Target.permissions10.intro,
              transitions = listOf(
                tr(on = "B", Finish("42")),
              ),
            ),
            "app.page1.login.credentials.permissions.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      sut.collectTransitions().test {
        awaitItem()

        sut.sendEvent(TestEvent("A"))
        awaitItem().active shouldBe "app.page1.login.credentials"

        sut.sendEvent(TestEvent("A"))
        awaitItem().active shouldBe "app.page1.login.credentials.permissions.intro"

        sut.sendEvent(TestEvent("B"))
        awaitItem().active shouldBe
          "app.page1.login.credentials.permissions.intro" // permissions flow receives B, sends "permissions finish"
        // login flow receives "permissions finish" sends "login finish"
        awaitItem().active shouldBe "app.page1.login.credentials.permissions.intro"
        awaitItem().active shouldBe "app.page1" // after "login finish"
      }
    }

    should("stay on current child node when Stay transition is returned") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService06Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app06.intro,
              transitions = listOf(
                tr(on = "A", Target.app06.test),
                tr(on = "B", Stay),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.intro.main" to TestScreenNode(),
            "app.intro.main.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
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

    should("consume event if Stay transition is returned") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService06Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app06.intro,
              transitions = listOf(
                tr(on = "A", Target.app06.test),
                tr(on = "B", Target.app06.intro),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.intro.main" to TestScreenNode(
              transitions = listOf(trs(on = "B", transition = Stay)),
            ),
            "app.intro.main.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
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
        TestNodeBuilder(
          NavService09ParentSchema(permissionsSchema = NavService09ChildSchema()),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app09.page1,
              transitions = listOf(
                tr(on = "A", Target.app09.permissions),
                tr<Nav09AppChildFinishRequest.Permissions>(Ignore),
              ),
            ),
            "app.page1" to TestScreenNode(),
            "app.page1.permissions" to TestFlowNode(
              initialTarget = Target.permissions09.intro,
              transitions = listOf(
                tr(on = "A", Target.permissions09.request),
              ),
            ),
            "app.page1.permissions.intro" to TestScreenNode(),
            "app.page1.permissions.intro.request" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
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
        TestNodeBuilder(
          NavService11Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app11.login,
              transitions = listOf(
                tr<Nav11AppChildFinishRequest.Login>(NavigateTo(Target.app11.main)),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.intro.main" to TestScreenNode(),
            "app.intro.main.test" to TestScreenNode(),
            "app.intro.main.test.login" to TestFlowNode(
              initialTarget = Target.login11.otp,
            ),
            "app.intro.main.test.login.credentials" to TestScreenNode(),
            "app.intro.main.test.login.credentials.otp" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit ->
          isFinished = true
          Ignore
        },
      )

      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.intro.main.test.login.credentials.otp"

        sut.sendEvent(Event.Back)
        awaitItem().active shouldBe "app.intro.main.test.login.credentials"

        sut.sendEvent(Event.Back)
        awaitItem().active shouldBe "app.intro.main.test.login.credentials" // back sends "finish"
        awaitItem().active shouldBe "app.intro.main" // after "finish"

        sut.sendEvent(Event.Back)
        awaitItem().active shouldBe "app.intro"

        isFinished shouldBe false
        sut.sendEvent(Event.Back)
        awaitItem().active shouldBe "app.intro" // back sends "finish"
        awaitItem().active shouldBe "app.intro" // after "finish"
        isFinished shouldBe true
      }
    }

    should("use service.onFinishRequest when receiving finish event") {
      var isFinished = false
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app01.intro,
              transitions = listOf(
                tr("A", Finish(33)),
              ),
            ),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { result: Int ->
          if (result == 33) {
            isFinished = true
          }
          Ignore
        },
      )

      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.intro"

        sut.sendEvent(TestEvent("A"))
        awaitItem().active shouldBe "app.intro" // back sends "finish"
        awaitItem().active shouldBe "app.intro" // after "finish"
        isFinished shouldBe true
      }
    }

    // Regression — real-world usage. Users cycle through child flows per session
    // (InstallationFlow, LoginFlow, UpdateFlow): enter, finish, re-enter. The child
    // NodeBuilder must be evicted on Finish so the second entry builds a fresh
    // instance. Without eviction the second entry resurrects the prior NodeBuilder
    // (and any scope-singleton it owns) — exactly the failure mode that
    // union-retain change was meant to fix at the multi-region level. This test
    // covers the single-region Finish-and-re-enter path.
    should("re-entering a child flow after Finish builds a fresh NodeBuilder (factory called twice)") {
      var loginNodeBuilderConstructions = 0
      val loginNodeBuilderFactory = object : ru.kode.way.nav10.LoginNodeBuilder.Factory {
        override fun createRootNode() = TestFlowNodeWithResult<Int>(
          initialTarget = Target.login10.credentials,
          dismissResult = 0,
          transitions = listOf(tr("FinishLogin", Finish(0))),
        )
        override fun createCredentialsNode() = TestScreenNode()
        override fun createPermissionsNodeBuilder() = error("not used")
      }
      val nodeBuilder = ru.kode.way.nav10.AppNodeBuilder(
        object : ru.kode.way.nav10.AppNodeBuilder.Factory {
          override fun createRootNode() = TestFlowNode(
            initialTarget = Target.app10.page1,
            transitions = listOf(
              tr("Enter", Target.app10.login),
              tr<Nav10AppChildFinishRequest.Login>(NavigateTo(Target.app10.page1)),
            ),
          )
          override fun createPage1Node() = TestScreenNode()
          override fun createPage2Node() = TestScreenNode()
          override fun createLoginNodeBuilder(): NodeBuilder {
            loginNodeBuilderConstructions++
            return ru.kode.way.nav10.LoginNodeBuilder(
              loginNodeBuilderFactory,
              NavService10LoginSchema(NavService10PermissionsSchema()),
            )
          }
        },
        NavService10Schema(NavService10LoginSchema(NavService10PermissionsSchema())),
      )

      val sut = NavigationService(nodeBuilder, onFinishRequest = { _: Unit -> Stay })

      sut.collectTransitions().test {
        awaitItem()
        sut.sendEvent(TestEvent("Enter"))
        awaitItem()
        loginNodeBuilderConstructions shouldBe 1

        sut.sendEvent(TestEvent("FinishLogin"))
        awaitItem()

        sut.sendEvent(TestEvent("Enter"))
        awaitItem()
        // Factory called TWICE — proves the first NodeBuilder was evicted on Finish
        // (union-retain saw no alive path starting with the login key) and the second
        // entry rebuilt fresh. If cache holds the entry too aggressively, this
        // would stay at 1.
        loginNodeBuilderConstructions shouldBe 2
        cancelAndIgnoreRemainingEvents()
      }
    }

    should("pass target arguments to flow, screen and sub-flow nodes") {
      val nodeBuilder = AppNodeBuilder(
        object : AppNodeBuilder.Factory {
          override fun createRootNode(timeout: Int) = TestFlowNode(
            initialTarget = Target.app12.page1(Charsets.UTF_32),
            payload = timeout,
            transitions = listOf(
              tr("A", Target.app12.login(defaultUserName = "Dima")),
              tr<Nav12AppChildFinishRequest.Login>(Stay),
            ),
          )

          override fun createPage2Node() = TestScreenNode()
          override fun createPage1Node(charset: Charset) = TestScreenNode(payload = charset)

          override fun createLoginNodeBuilder(defaultUserName: String): NodeBuilder = LoginNodeBuilder(
            object : LoginNodeBuilder.Factory {
              override fun createRootNode(defaultUserName: String) = TestFlowNode(
                initialTarget = Target.login12.credentials(defaultPhone = "+7981123456"),
                payload = defaultUserName,
                transitions = listOf(
                  tr("B", Target.login12.otp(useAnimation = true)),
                ),
              )

              override fun createCredentialsNode(defaultPhone: String) = TestScreenNode(payload = defaultPhone)
              override fun createOtpNode(useAnimation: Boolean) = TestScreenNode(payload = useAnimation)
            },
            NavService12LoginSchema(),
          )
        },
        NavService12Schema(NavService12LoginSchema()),
      )

      val sut = NavigationService(
        nodeBuilder,
        onFinishRequest = { _: Int -> Ignore },
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

    // Added persistent state.payloads (transient payloads
    // were merged into the persistent map after each transition). Explicitly
    // excluded InitEvent payloads from that store, because their length-1 keys would crash
    // the codegen's `mapKeys { drop(N) }` cascade once a deeper navigation drops past the
    // payload's depth. The InitEvent payload reaches the root node via the direct first-build
    // call and never needs to round-trip through `state.payloads` afterwards (the root flow
    // is built once).
    should("InitEvent root payload is not persisted in state.payloads after a deeper navigation") {
      val nodeBuilder = AppNodeBuilder(
        object : AppNodeBuilder.Factory {
          override fun createRootNode(timeout: Int) = TestFlowNode(
            initialTarget = Target.app12.page1(Charsets.UTF_32),
            payload = timeout,
            transitions = listOf(
              tr("A", Target.app12.login(defaultUserName = "Dima")),
              tr<Nav12AppChildFinishRequest.Login>(Stay),
            ),
          )
          override fun createPage2Node() = TestScreenNode()
          override fun createPage1Node(charset: Charset) = TestScreenNode(payload = charset)
          override fun createLoginNodeBuilder(defaultUserName: String): NodeBuilder = LoginNodeBuilder(
            object : LoginNodeBuilder.Factory {
              override fun createRootNode(defaultUserName: String) = TestFlowNode(
                initialTarget = Target.login12.credentials(defaultPhone = "+7981123456"),
                payload = defaultUserName,
              )
              override fun createCredentialsNode(defaultPhone: String) = TestScreenNode(payload = defaultPhone)
              override fun createOtpNode(useAnimation: Boolean) = TestScreenNode(payload = useAnimation)
            },
            NavService12LoginSchema(),
          )
        },
        NavService12Schema(NavService12LoginSchema()),
      )

      val sut = NavigationService(nodeBuilder, onFinishRequest = { _: Int -> Ignore })

      sut.collectTransitions(rootNodePayload = 42).test {
        awaitItem().apply {
          // After the initial transition the InitEvent payload reached the root node
          // (verified by the existing "pass target arguments" test), but it MUST NOT have
          // been recorded in state.payloads — only NavigateTo payloads belong there. If
          // guard regresses, the length-1 root key `Path("app")` ends up in
          // state.payloads and is fed into the next deeper cascade, where the
          // `payloads.filterKeys { it.length > N }.mapKeys { drop(N) }` chain at the first
          // hop produces an empty Path mid-`mapKeys` and crashes Path's init.
          payloads.keys.map { it.toString() }.shouldNotContainAnyOf(listOf("app"))
        }

        sut.sendEvent(TestEvent("A"))
        awaitItem().apply {
          // After NavigateTo Target.app12.login("Dima"), the login flow's payload IS
          // persisted (NavigateTo payloads are intentionally durable so a later rebuild of
          // the child flow's NodeBuilder can read its parameter without the caller
          // re-supplying it). The root's length-1 key still must not appear.
          payloads.keys.map { it.toString() }.shouldNotContainAnyOf(listOf("app"))
          payloads.keys.map { it.toString() }.shouldContainInOrder(listOf("app.page1.login"))
        }
      }
    }

    should("correctly call onEntry/onExit in basic cases") {
      val entryCounts = mutableMapOf<String, Int>()
      val exitCounts = mutableMapOf<String, Int>()
      val sut = NavigationService(
        TestNodeBuilder(
          NavService10Schema(NavService10LoginSchema(NavService10PermissionsSchema())),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app10.page1,
              onEntryImpl = { entryCounts["app"] = entryCounts["app"]?.let { it + 1 } ?: 1 },
              onExitImpl = { exitCounts["app"] = exitCounts["app"]?.let { it + 1 } ?: 1 },
              transitions = listOf(
                tr("A", Target.app10.login),
                tr<Nav10AppChildFinishRequest.Login>(NavigateTo(Target.app10.page1)),
              ),
            ),
            "app.page1" to TestScreenNode(
              onEntryImpl = { entryCounts["app.page1"] = entryCounts["app.page1"]?.let { it + 1 } ?: 1 },
              onExitImpl = { exitCounts["app.page1"] = exitCounts["app.page1"]?.let { it + 1 } ?: 1 },
              transitions = listOf(
                trs("B", Target.app10.page2),
              ),
            ),
            "app.page2" to TestScreenNode(
              onEntryImpl = { entryCounts["app.page2"] = entryCounts["app.page2"]?.let { it + 1 } ?: 1 },
              onExitImpl = { exitCounts["app.page2"] = exitCounts["app.page2"]?.let { it + 1 } ?: 1 },
              transitions = listOf(
                trs("A", Target.app10.login),
                trs<Nav10AppChildFinishRequest.Login>(NavigateTo(Target.app10.page1)),
              ),
            ),
            "app.page1.login" to TestFlowNodeWithResult(
              initialTarget = Target.login10.credentials,
              dismissResult = 33,
              onEntryImpl = { entryCounts["app.page1.login"] = entryCounts["app.page1.login"]?.let { it + 1 } ?: 1 },
              onExitImpl = { exitCounts["app.page1.login"] = exitCounts["app.page1.login"]?.let { it + 1 } ?: 1 },
              transitions = listOf(
                tr(on = "A", target = Target.login10.permissions),
                tr<Nav10LoginChildFinishRequest.Permissions>(Finish(44)),
              ),
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
              ),
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
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      entryCounts.shouldBeEmpty()
      exitCounts.shouldBeEmpty()

      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.page1"
        entryCounts.shouldContainExactly(
          mapOf("app" to 1, "app.page1" to 1),
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
          ),
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
          ),
        )
        exitCounts.shouldBeEmpty()

        sut.sendEvent(TestEvent("B"))
        awaitItem().active shouldBe "app.page1.login.credentials.permissions.intro" // sends "permissions finish"
        awaitItem().active shouldBe
          "app.page1.login.credentials.permissions.intro" // after "permissions finish" -> sends "login finish"
        awaitItem().active shouldBe "app.page1" // after "login finish" -> sends "finish"
        entryCounts.shouldContainExactly(
          mapOf(
            "app" to 1,
            "app.page1" to 1,
            "app.page1.login" to 1,
            "app.page1.login.credentials" to 1,
            "app.page1.login.credentials.permissions" to 1,
            "app.page1.login.credentials.permissions.intro" to 1,
          ),
        )
        exitCounts.shouldContainExactly(
          mapOf(
            "app.page1.login.credentials.permissions.intro" to 1,
            "app.page1.login.credentials.permissions" to 1,
            "app.page1.login.credentials" to 1,
            "app.page1.login" to 1,
          ),
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
          ),
        )
        exitCounts.shouldContainExactly(
          mapOf(
            "app.page1.login.credentials.permissions.intro" to 1,
            "app.page1.login.credentials.permissions" to 1,
            "app.page1.login.credentials" to 1,
            "app.page1.login" to 1,
            "app.page1" to 1,
          ),
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
          ),
        )
        exitCounts.shouldContainExactly(
          mapOf(
            "app.page1.login.credentials.permissions.intro" to 1,
            "app.page1.login.credentials.permissions" to 1,
            "app.page1.login.credentials" to 1,
            "app.page1.login" to 1,
            "app.page1" to 1,
            "app.page2" to 1,
          ),
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
        ru.kode.way.nav09.AppNodeBuilder(
          object : ru.kode.way.nav09.AppNodeBuilder.Factory {
            override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.app09.permissions)

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
                NavService09ChildSchema(),
              )
            }
          },
          NavService09ParentSchema(NavService09ChildSchema()),
        ),
        onFinishRequest = { Ignore },
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
        ru.kode.way.nav09.AppNodeBuilder(
          object : ru.kode.way.nav09.AppNodeBuilder.Factory {
            override fun createRootNode(): FlowNode<*> = TestFlowNode(
              initialTarget = Target.app09.permissions,
              transitions = listOf(
                tr(on = "A", NavigateTo(Target.app09.page1)),
                tr(on = "B", NavigateTo(Target.app09.permissions)),
              ),
            )

            override fun createPage1Node() = TestScreenNode()

            override fun createPermissionsNodeBuilder(): NodeBuilder = PermissionsNodeBuilder(
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
                    ),
                  )
                }
                override fun createRootNode(): FlowNode<*> = flowNode
                override fun createIntroNode() = TestScreenNode()
                override fun createRequestNode() = TestScreenNode()
              },
              NavService09ChildSchema(),
            )
          },
          NavService09ParentSchema(NavService09ChildSchema()),
        ),
        onFinishRequest = { Ignore },
      )

      sut.collectTransitions().test {
        var state = awaitItem()
        state.active shouldBe "app.page1.permissions.intro.request"
        val permissionNodes = state.aliveNodes.filter { it.key.contains("permissions") }.values

        sut.sendEvent(TestEvent("A"))
        awaitItem().active shouldBe "app.page1"

        // PermissionsNodeBuilder should be released inside AppNodeBuilder at this point and on "B"-event it should
        // be reconstructed again

        sut.sendEvent(TestEvent("B"))
        state = awaitItem()
        state.active shouldBe "app.page1.permissions.intro.request"
        val newPermissionNodes = state.aliveNodes.filter { it.key.contains("permissions") }.values
        newPermissionNodes.shouldNotContainAnyOf(permissionNodes)
        val requestNode = state.aliveNodes.entries.find { it.key.endsWith("request") }!!.value

        sut.sendEvent(TestEvent("C"))
        awaitItem()
        sut.sendEvent(TestEvent("D"))
        val newRequestNode = awaitItem().aliveNodes.entries.find { it.key.endsWith("request") }!!.value

        requestNode shouldNotBe newRequestNode

        cancelAndIgnoreRemainingEvents()
      }
    }

    should("correctly transition when given an absolute target") {
      val loginFlowTarget = Target.app12.login(defaultUserName = "Gregoriy")
      val loginCredentialsTarget = Target.login12.credentials(defaultPhone = "800")
      val loginOtpTarget = Target.login12.otp(useAnimation = true)
      val rootSegment = NavService12Schema(NavService12LoginSchema()).rootSegment
      // loginSchemaRoot is the absolute path to the login sub-schema boundary ("app/page1/login").
      // loginOtpTarget.path is relative to the login schema root and contains two segments
      // ("credentials/otp"), so we can't use the AbsoluteTarget(rootSegment, vararg hops)
      // convenience constructor here — the credentials segment would be duplicated.
      val loginSchemaRoot = Path(rootSegment).append(loginFlowTarget.path)
      val absoluteTarget = AbsoluteTarget(
        path = loginSchemaRoot.append(loginOtpTarget.path),
        payloads = mapOf(
          loginSchemaRoot to loginFlowTarget.payload!!,
          loginSchemaRoot.append(loginCredentialsTarget.path) to loginCredentialsTarget.payload!!,
          loginSchemaRoot.append(loginOtpTarget.path) to loginOtpTarget.payload!!,
        ),
      )

      val nodeBuilder = AppNodeBuilder(
        object : AppNodeBuilder.Factory {
          override fun createRootNode(timeout: Int) = TestFlowNode(
            initialTarget = Target.app12.page1(Charsets.UTF_32),
            payload = timeout,
            transitions = listOf(
              tr("A", absoluteTarget),
            ),
          )

          override fun createPage2Node() = TestScreenNode()
          override fun createPage1Node(charset: Charset) = TestScreenNode(payload = charset)

          override fun createLoginNodeBuilder(defaultUserName: String): NodeBuilder = LoginNodeBuilder(
            object : LoginNodeBuilder.Factory {
              override fun createRootNode(defaultUserName: String) = TestFlowNode(
                initialTarget = Target.login12.credentials(defaultPhone = "+7981123456"),
                payload = defaultUserName,
              )

              override fun createCredentialsNode(defaultPhone: String) = TestScreenNode(payload = defaultPhone)
              override fun createOtpNode(useAnimation: Boolean) = TestScreenNode(payload = useAnimation)
            },
            NavService12LoginSchema(),
          )
        },
        NavService12Schema(NavService12LoginSchema()),
      )

      val sut = NavigationService(
        nodeBuilder,
        onFinishRequest = { _: Int -> Ignore },
      )

      sut.collectTransitions(rootNodePayload = 42).test {
        awaitItem()

        sut.sendEvent(TestEvent("A"))

        awaitItem().apply {
          active shouldBe "app.page1.login.credentials.otp"
          (aliveNodes["app.page1.login"] as TestFlowNode?)?.payload shouldBe "Gregoriy"
          (aliveNodes["app.page1.login.credentials"] as TestScreenNode?)?.payload shouldBe "800"
          (aliveNodes["app.page1.login.credentials.otp"] as TestScreenNode?)?.payload shouldBe true
        }
      }
    }

    should("addTransitionListener after start gets immediate state callback") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )
      sut.start()

      var receivedState: NavigationState? = null
      sut.addTransitionListener { state -> receivedState = state }

      receivedState shouldNotBe null
      receivedState!!.active shouldBe "app.intro"
    }

    should("regionByName returns the correct region and null for unknown name") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )

      sut.collectTransitions().test {
        val state = awaitItem()
        state.regionByName("app") shouldNotBe null
        state.regionByName("nonexistent") shouldBe null
      }
    }

    should("removeTransitionListener stops further state deliveries") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(tr("A", Target.app05.main)),
            ),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      val deliveries = mutableListOf<String>()
      val listener: (NavigationState) -> Unit = { state -> deliveries.add(state.active) }
      sut.addTransitionListener(listener)
      sut.start() // → "app.intro"

      sut.removeTransitionListener(listener)
      sut.sendEvent(TestEvent("A")) // should NOT be delivered

      deliveries shouldBe listOf("app.intro")
    }

    should("listener calling sendEvent during dispatch queues event and processes it after current dispatch") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(
                tr("A", Target.app05.main),
                tr("B", Target.app05.test),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      val states = mutableListOf<String>()
      sut.addTransitionListener { state ->
        states.add(state.active)
        if (state.active == "app.main") {
          sut.sendEvent(TestEvent("B")) // reentrant call — must be queued, not immediate
        }
      }
      sut.start() // emits "app.intro"
      sut.sendEvent(TestEvent("A")) // emits "app.main", queues B, then drains: emits "app.test"

      states shouldBe listOf("app.intro", "app.main", "app.test")
    }

    should("EnqueueEvent from ScreenNode enqueues event which is dispatched after current transition") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(
                tr("navigate", Target.app05.main),
              ),
            ),
            "app.intro" to TestScreenNode(
              transitions = listOf(
                trs("enqueue", EnqueueEvent(TestEvent("navigate"))),
              ),
            ),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.intro"

        sut.sendEvent(TestEvent("enqueue"))
        // First: screen returns EnqueueEvent — state stays at app.intro, "navigate" is queued
        awaitItem().active shouldBe "app.intro"
        // Second: queued "navigate" is dispatched — navigates to app.main
        awaitItem().active shouldBe "app.main"
      }
    }

    should("not call checkSchemaValidity when validateSchema is false") {
      // Use a mismatched node (TestParallelNode where schema declares a ScreenNode).
      // With validateSchema = true this would throw; with false it must silently pass.
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            // Intentionally wrong node type — ParallelFlowNode<Unit> where schema says Screen.
            "app.intro" to TestParallelNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )
      sut.validateSchema = false

      sut.collectTransitions().test {
        // Should not throw despite the type mismatch
        awaitItem().active shouldBe "app.intro"
      }
    }

    should("send Back from root screen calls onFinishRequest and state is unchanged when it returns Stay") {
      var finishCalled = false
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNodeWithResult(initialTarget = Target.app01.intro, dismissResult = 42),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int ->
          finishCalled = true
          Stay
        },
      )

      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.intro"
        sut.sendEvent(Event.Back)
        // Back triggers Finish(42), which sends RootFinishRequestEvent; Stay keeps app.intro.
        // Two emissions: one for Back transition, one for RootFinishRequestEvent + Stay.
        awaitItem().active shouldBe "app.intro"
        awaitItem().active shouldBe "app.intro"
        finishCalled shouldBe true
      }
    }

    should("dispose clears listeners so no further state is delivered") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNodeWithResult(initialTarget = Target.app01.intro, dismissResult = 42),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )

      val deliveries = mutableListOf<String>()
      val listener: (NavigationState) -> Unit = { state -> deliveries.add(state.active) }
      sut.addTransitionListener(listener)
      sut.start() // emits "app.intro"

      deliveries shouldBe listOf("app.intro")

      sut.dispose()

      // After dispose, sendEvent must not deliver to listener
      sut.sendEvent(TestEvent("anything"))

      deliveries shouldBe listOf("app.intro")
    }

    should("sendEvent after dispose does not throw") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNodeWithResult(initialTarget = Target.app01.intro, dismissResult = 42),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )
      sut.start()
      sut.dispose()

      // Must not throw
      sut.sendEvent(TestEvent("anything"))
      sut.sendEvent(TestEvent("more"))
    }

    should("state is rolled back when NodeBuilder throws during a transition") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(tr("A", Target.app05.main)),
            ),
            "app.intro" to TestScreenNode(),
            // "app.main" intentionally absent → NodeBuilder.build() throws during synchronizeNodes
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      sut.start()

      // The failing transition throws out of sendEvent
      val ex = runCatching { sut.sendEvent(TestEvent("A")) }.exceptionOrNull()
      ex shouldNotBe null

      // C3 rollback: state must reflect the pre-transition snapshot, not a partial mutation
      var capturedState: NavigationState? = null
      sut.addTransitionListener { capturedState = it }
      capturedState shouldNotBe null
      capturedState!!.active shouldBe "app.intro"
      capturedState!!._enqueuedEvents.isEmpty() shouldBe true
      // alive list and nodes must be fully restored — partial mutation of alive would surface
      // as a runValidityChecks failure on the very next sendEvent
      val region = capturedState!!.regions.values.first()
      region.alive.map { it.toString() } shouldBe listOf("app", "app.intro")
      region.nodes.keys.map { it.toString() }.toSet() shouldBe setOf("app", "app.intro")
    }

    should("_enqueuedEvents is empty after rollback triggered by a chained enqueued event failure") {
      // Event "A" produces EnqueueEvent("B"); "B" tries NavigateTo(app.main) which is missing.
      // The drain pops B before invoking its transition, so when B's transition fails the
      // pre-B snapshot of _enqueuedEvents was already empty. The rollback restores that snapshot
      // (snapshot semantics introduced for the core-4 fix), so the queue is empty post-failure.
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(
                tr("A", EnqueueEvent(TestEvent("B"))),
                tr("B", Target.app05.main),
              ),
            ),
            "app.intro" to TestScreenNode(),
            // "app.main" intentionally absent → NodeBuilder.build() throws when B is drained
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      sut.start()

      val ex = runCatching { sut.sendEvent(TestEvent("A")) }.exceptionOrNull()
      ex shouldNotBe null

      var capturedState: NavigationState? = null
      sut.addTransitionListener { capturedState = it }
      capturedState shouldNotBe null
      capturedState!!.active shouldBe "app.intro"
      capturedState!!._enqueuedEvents.isEmpty() shouldBe true
      val region = capturedState!!.regions.values.first()
      region.alive.map { it.toString() } shouldBe listOf("app", "app.intro")
      region.nodes.keys.map { it.toString() }.toSet() shouldBe setOf("app", "app.intro")
    }

    should("listener exception propagates immediately; enqueued events are not drained") {
      // If a listener throws, the iterative drain loop exits immediately via the exception.
      // Enqueued events (B in this case) are left in the queue but not processed.
      // Setup: A → EnqueueEvent(B), B → NavigateTo(app.test).
      // Listener throws when it sees the A-dispatch state (app.intro unchanged).
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(
                tr("A", EnqueueEvent(TestEvent("B"))),
                tr("B", Target.app05.test),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      var capturedState: NavigationState? = null
      var shouldThrow = false
      sut.addTransitionListener { state ->
        if (shouldThrow && state.active == "app.intro") throw RuntimeException("listener error")
      }
      sut.addTransitionListener { capturedState = it }
      sut.start()
      shouldThrow = true

      val ex = runCatching { sut.sendEvent(TestEvent("A")) }.exceptionOrNull()
      // The listener exception propagates to the caller
      ex?.message shouldBe "listener error"
      // B was NOT drained — state is still at app.intro (capturedState from start(), not updated)
      capturedState!!.active shouldBe "app.intro"
    }

    should("sendEvent before start throws IllegalStateException") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )

      io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
        sut.sendEvent(TestEvent("anything"))
      }
    }

    should("EnqueueEvent chain of 3 drains in order") {
      // Event "A" -> EnqueueEvent("B"), "B" -> EnqueueEvent("C"), "C" -> NavigateTo(test)
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(
                tr("A", EnqueueEvent(TestEvent("B"))),
                tr("B", EnqueueEvent(TestEvent("C"))),
                tr("C", Target.app05.test),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.intro"

        sut.sendEvent(TestEvent("A"))
        awaitItem().active shouldBe "app.intro" // A -> EnqueueEvent(B), state stays
        awaitItem().active shouldBe "app.intro" // B -> EnqueueEvent(C), state stays
        awaitItem().active shouldBe "app.test" // C -> NavigateTo(test)
      }
    }

    should("onPostTransition throwing during start() propagates exception but leaves service started") {
      // onPostTransition fires after the transition is fully committed. An exception there must NOT
      // roll back navigation state — the init succeeded and the service must remain usable.
      var onExitCalled = false
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app01.intro,
              onExitImpl = {
                onExitCalled = true
              },
            ),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )

      sut.addServiceExtensionPoint(object : ServiceExtensionPoint<Int> {
        override fun onPreTransition(service: NavigationService<Int>, event: Event, state: NavigationState) {}
        override fun onPostTransition(service: NavigationService<Int>, event: Event, state: NavigationState) {
          if (event is InitEvent) throw RuntimeException("onPostTransition failed during start")
        }
      })

      val ex = runCatching { sut.start() }.exceptionOrNull()
      ex shouldNotBe null // exception propagates to start() caller
      onExitCalled shouldBe false // no compensation — transition already committed
      sut.isStarted() shouldBe true // service is alive; init was not undone
    }

    should("NavigateTo with multiple targets in the same region — last target wins") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(
                tr("go", NavigateTo(listOf(Target.app05.main, Target.app05.test))),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.intro"
        sut.sendEvent(TestEvent("go"))
        awaitItem().active shouldBe "app.test"
        cancelAndIgnoreRemainingEvents()
      }
    }

    should(
      "NavigateTo same-region targets resolve by list order — the LAST occurrence wins, duplicates are idempotent",
    ) {
      // Locks the List<Target> contract: same-region resolution is last-wins BY ORDER, and a
      // repeated target is idempotent (no crash). This is exactly where a Set<Target> would
      // diverge — setOf(main, test, main) would drop the trailing `main` and land on `test`,
      // whereas the ordered List keeps the last occurrence and lands on `main`.
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(
                tr("go", NavigateTo(listOf(Target.app05.main, Target.app05.test, Target.app05.main))),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.intro"
        sut.sendEvent(TestEvent("go"))
        awaitItem().active shouldBe "app.main" // last occurrence in the list, not `test`
        cancelAndIgnoreRemainingEvents()
      }
    }

    // ── cleanDispose ──────────────────────────────────────────────────────────

    should("cleanDispose calls onDispose on all alive nodes") {
      val disposed = mutableListOf<String>()
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app01.intro,
              onDisposeImpl = { disposed.add("app") },
            ),
            "app.intro" to TestScreenNode(onDisposeImpl = { disposed.add("app.intro") }),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )
      sut.start()

      sut.cleanDispose()

      disposed shouldContainInOrder listOf("app.intro", "app")
    }

    should("cleanDispose calls onDispose leaf-to-root within a region") {
      val disposed = mutableListOf<String>()
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              onDisposeImpl = { disposed.add("app") },
              transitions = listOf(tr("go", NavigateTo(Target.app05.main))),
            ),
            "app.intro" to TestScreenNode(onDisposeImpl = { disposed.add("app.intro") }),
            "app.main" to TestScreenNode(onDisposeImpl = { disposed.add("app.main") }),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.intro"
        sut.sendEvent(TestEvent("go"))
        awaitItem().active shouldBe "app.main"
        cancelAndIgnoreRemainingEvents()
      }

      sut.cleanDispose()

      // alive after navigation: app, app.main — intro was exited during navigation
      disposed shouldContainInOrder listOf("app.main", "app")
      disposed shouldNotContainAnyOf listOf("app.intro")
    }

    should("cleanDispose disposes sub-region nodes before parent parallel node") {
      val disposed = mutableListOf<String>()

      val appSchema = ru.kode.way.par01.Parallel01Schema(
        par01MainSchema = ru.kode.way.par01.main.Parallel01MainSchema(
          par01TopSchema = ru.kode.way.par01.top.Parallel01TopSchema(),
          par01BottomSchema = ru.kode.way.par01.bottom.Parallel01BottomSchema(),
        ),
      )
      val topNodeBuilder = ru.kode.way.par01.top.Par01TopNodeBuilder(
        nodeFactory = object : ru.kode.way.par01.top.Par01TopNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par01Top.par01TopIntro,
            onDisposeImpl = { disposed.add("par01Top") },
          )
          override fun createPar01TopIntroNode(): ScreenNode = TestScreenNode(
            onDisposeImpl = { disposed.add("par01TopIntro") },
          )
        },
        schema = ru.kode.way.par01.top.Parallel01TopSchema(),
      )
      val bottomNodeBuilder = ru.kode.way.par01.bottom.Par01BottomNodeBuilder(
        nodeFactory = object : ru.kode.way.par01.bottom.Par01BottomNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par01Bottom.par01BottomMain,
            onDisposeImpl = { disposed.add("par01Bottom") },
          )
          override fun createPar01BottomMainNode(): ScreenNode = TestScreenNode(
            onDisposeImpl = { disposed.add("par01BottomMain") },
          )
        },
        schema = ru.kode.way.par01.bottom.Parallel01BottomSchema(),
      )
      val mainNodeBuilder = ru.kode.way.par01.main.Par01MainNodeBuilder(
        nodeFactory = object : ru.kode.way.par01.main.Par01MainNodeBuilder.Factory {
          override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode(
            onDisposeImpl = { disposed.add("par01Main") },
          )
          override fun createPar01BottomNodeBuilder(): NodeBuilder = bottomNodeBuilder
          override fun createPar01TopNodeBuilder(): NodeBuilder = topNodeBuilder
        },
        ru.kode.way.par01.main.Parallel01MainSchema(
          ru.kode.way.par01.top.Parallel01TopSchema(),
          ru.kode.way.par01.bottom.Parallel01BottomSchema(),
        ),
      )
      val appNodeBuilder = ru.kode.way.par01.Par01AppNodeBuilder(
        nodeFactory = object : ru.kode.way.par01.Par01AppNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par01App.par01Main,
            onDisposeImpl = { disposed.add("par01App") },
          )
          override fun createPar01MainNodeBuilder(): NodeBuilder = mainNodeBuilder
        },
        schema = appSchema,
      )
      val sut: NavigationService<Unit> = NavigationService(nodeBuilder = appNodeBuilder, onFinishRequest = { Ignore })
      sut.start()

      sut.cleanDispose()

      // within each sub-region: screen before its flow (leaf → root)
      disposed.indexOf("par01TopIntro") shouldBeLessThan disposed.indexOf("par01Top")
      disposed.indexOf("par01BottomMain") shouldBeLessThan disposed.indexOf("par01Bottom")
      // both sub-regions fully disposed before the parallel node and app flow
      disposed.indexOf("par01Top") shouldBeLessThan disposed.indexOf("par01Main")
      disposed.indexOf("par01Bottom") shouldBeLessThan disposed.indexOf("par01Main")
      disposed.indexOf("par01Main") shouldBeLessThan disposed.indexOf("par01App")
    }

    should("cleanDispose continues when one node's onDispose throws") {
      val disposed = mutableListOf<String>()
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app01.intro,
              onDisposeImpl = { disposed.add("app") },
            ),
            "app.intro" to TestScreenNode(
              onDisposeImpl = {
                disposed.add("app.intro")
                error("onDispose threw")
              },
            ),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )
      sut.start()

      sut.cleanDispose()

      disposed shouldContainInOrder listOf("app.intro", "app")
    }

    should("cleanDispose on an unstarted service does not throw") {
      val sut = NavigationService(
        TestNodeBuilder(NavService01Schema(), mapOf("app" to TestFlowNode(Target.app01.intro))),
        onFinishRequest = { _: Int -> Stay },
      )
      sut.cleanDispose()
    }

    should("cleanDispose is idempotent") {
      val disposeCount = mutableListOf<String>()
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app01.intro,
              onDisposeImpl = { disposeCount.add("app") },
            ),
            "app.intro" to TestScreenNode(onDisposeImpl = { disposeCount.add("app.intro") }),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )
      sut.start()

      sut.cleanDispose()
      sut.cleanDispose()

      disposeCount.size shouldBe 2
    }

    should("sendEvent after cleanDispose is a no-op") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(tr("go", NavigateTo(Target.app05.main))),
            ),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      sut.start()
      sut.cleanDispose()

      sut.sendEvent(TestEvent("go")) // must not throw
    }

    should("cleanDispose fires onPreDispose and onPostDispose extension-point hooks") {
      val hookLog = mutableListOf<String>()
      val extensionPoint = TestNodeExtensionPoint(
        preDispose = { _, path -> hookLog.add("pre:$path") },
        postDispose = { _, path -> hookLog.add("post:$path") },
      )
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app05.intro),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      sut.addNodeExtensionPoint(extensionPoint)
      sut.start()

      sut.cleanDispose()

      // Both pre and post hooks must fire for every alive node
      hookLog.filter { it.startsWith("pre:") }.map { it.removePrefix("pre:") }
        .shouldContainInOrder("app.intro", "app")
      hookLog.filter { it.startsWith("post:") }.map { it.removePrefix("post:") }
        .shouldContainInOrder("app.intro", "app")
      // pre always before post for each path
      hookLog.indexOf("pre:app.intro") shouldBeLessThan hookLog.indexOf("post:app.intro")
      hookLog.indexOf("pre:app") shouldBeLessThan hookLog.indexOf("post:app")
    }

    should("queued events that survived prior successful iterations remain after a later transition fails") {
      // core-4: snapshot _enqueuedEvents on rollback so events appended by earlier iterations of
      // the same sendEvent drain are NOT discarded when a later transition fails.
      // Scenario:
      //  - sendEvent("A") with a transition that navigates to "main".
      //  - A listener observes the "main" state and, while still inside dispatch
      //    (isDispatching == true), recursively calls sendEvent("B") and sendEvent("C").
      //    Both are appended to _enqueuedEvents.
      //  - The drain pops "B" first; B's transition tries NavigateTo(app.missing) and fails.
      //  - Without the snapshot fix, the catch would clear() the whole queue, losing C.
      //  - With the snapshot fix, the pre-B snapshot ([C]) is restored, so C survives.
      var listenerArmed = false
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(
                tr("A", Target.app05.main),
                // "B" navigates to "app.test", which is declared in the schema but intentionally
                // absent from the NodeBuilder map below → B's transition fails inside synchronizeNodes.
                tr("B", Target.app05.test),
                // "C" is the survivor — it never gets to run because B fails first; we only care
                // that it remains queued after the rollback.
                tr("C", Target.app05.main),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            // "app.test" intentionally absent → B's transition fails when synchronizeNodes builds it
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      sut.addTransitionListener { state ->
        if (listenerArmed && state.active == "app.main") {
          listenerArmed = false
          sut.sendEvent(TestEvent("B"))
          sut.sendEvent(TestEvent("C"))
        }
      }
      sut.start()

      listenerArmed = true
      val ex = runCatching { sut.sendEvent(TestEvent("A")) }.exceptionOrNull()
      ex shouldNotBe null // B's failure propagated out of sendEvent

      // C must still be queued — the snapshot rollback preserved events queued before B failed.
      var capturedState: NavigationState? = null
      sut.addTransitionListener { capturedState = it }
      capturedState shouldNotBe null
      capturedState!!._enqueuedEvents.filterIsInstance<TestEvent>().map { it.name } shouldBe listOf("C")
    }

    should("RootFinishRequestEvent targeted at one sub-region is NOT delivered to other sub-regions") {
      // core-5: when a sub-region's flow Finishes, the runtime emits RootFinishRequestEvent with
      // targetRegionId pointing at that sub-region. The event must NOT reach Node.transition or
      // NodeExtensionPoint.onPreTransition for any other region — that would leak an internal
      // event class to user code in unrelated parts of the graph.
      val preTransitionLog = mutableListOf<Pair<String, String>>() // (path, event-class)
      val appSchema = ru.kode.way.par03.Parallel03Schema(
        par03MainSchema = ru.kode.way.par03.main.Parallel03MainSchema(
          par03AlphaSchema = ru.kode.way.par03.alpha.Parallel03AlphaSchema(),
          par03BetaSchema = ru.kode.way.par03.beta.Parallel03BetaSchema(),
        ),
      )
      val alphaNodeBuilder = ru.kode.way.par03.alpha.Par03AlphaNodeBuilder(
        nodeFactory = object : ru.kode.way.par03.alpha.Par03AlphaNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(
            initialTarget = Target.par03Alpha.par03AlphaScreen,
            transitions = listOf(tr("finishAlpha", Finish(Unit))),
          )
          override fun createPar03AlphaScreenNode(): ScreenNode = TestScreenNode()
          override fun createPar03AlphaScreen2Node(): ScreenNode = TestScreenNode()
        },
        schema = ru.kode.way.par03.alpha.Parallel03AlphaSchema(),
      )
      val betaNodeBuilder = ru.kode.way.par03.beta.Par03BetaNodeBuilder(
        nodeFactory = object : ru.kode.way.par03.beta.Par03BetaNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par03Beta.par03BetaScreen)
          override fun createPar03BetaScreenNode(): ScreenNode = TestScreenNode()
        },
        schema = ru.kode.way.par03.beta.Parallel03BetaSchema(),
      )
      val mainNodeBuilder = ru.kode.way.par03.main.Par03MainNodeBuilder(
        nodeFactory = object : ru.kode.way.par03.main.Par03MainNodeBuilder.Factory {
          override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
          override fun createPar03AlphaNodeBuilder(): NodeBuilder = alphaNodeBuilder
          override fun createPar03BetaNodeBuilder(): NodeBuilder = betaNodeBuilder
        },
        schema = ru.kode.way.par03.main.Parallel03MainSchema(
          ru.kode.way.par03.alpha.Parallel03AlphaSchema(),
          ru.kode.way.par03.beta.Parallel03BetaSchema(),
        ),
      )
      val appNodeBuilder = ru.kode.way.par03.Par03AppNodeBuilder(
        nodeFactory = object : ru.kode.way.par03.Par03AppNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par03App.par03Main)
          override fun createPar03MainNodeBuilder(): NodeBuilder = mainNodeBuilder
          override fun createPar03PageNode(): ScreenNode = TestScreenNode()
        },
        schema = appSchema,
      )
      val sut = NavigationService<Unit>(
        nodeBuilder = appNodeBuilder,
        onFinishRequest = { Ignore },
      )
      sut.addNodeExtensionPoint(
        TestNodeExtensionPoint(
          preTransition = { _, path, event ->
            preTransitionLog.add(path.toString() to event::class.simpleName.orEmpty())
          },
        ),
      )
      sut.start()

      preTransitionLog.clear()
      sut.sendEvent(TestEvent("finishAlpha"))

      // The internal RootFinishRequestEvent must NOT have been delivered to any node in the
      // non-target (beta) region or in the app region. The only "regions" entitled to process it
      // are: the targeted sub-region's flow root (via rootTransitionBuilder, which bypasses
      // Node.transition and is not recorded by onPreTransition) and re-entries triggered by it.
      val rootFinishLeaks = preTransitionLog.filter { (_, eventClass) ->
        eventClass == "RootFinishRequestEvent"
      }
      rootFinishLeaks shouldBe emptyList()
    }

    should("addTransitionListener whose replay throws is removed before sendEvent") {
      // test-13: when a listener throws during the immediate-replay performed at registration
      // time, the listener must be removed so it is NOT invoked again on subsequent transitions.
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(tr("go", Target.app05.main)),
            ),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      sut.start()

      var invocationCount = 0
      val ex = runCatching {
        sut.addTransitionListener {
          invocationCount += 1
          if (invocationCount == 1) error("boom on replay")
        }
      }.exceptionOrNull()
      ex shouldNotBe null
      invocationCount shouldBe 1

      // The listener should have been removed at replay time — a subsequent transition must
      // NOT invoke it a second time.
      sut.sendEvent(TestEvent("go"))
      invocationCount shouldBe 1
    }

    should("AbsoluteTarget used as FlowNode.initial throws a clear error on start()") {
      // test-15: AbsoluteTarget is only valid for runtime navigation (NavigateTo), never as the
      // declared initial of a flow. When maybeResolveInitial recurses into a flow whose initial is
      // an AbsoluteTarget it must throw a clear, user-facing error.
      // We use the nav02 schema (app → permissions (flow) → intro) so that the runtime recurses
      // into the inner flow's `initial`, hitting the AbsoluteTarget branch of maybeResolveInitial.
      val sut = NavigationService(
        TestNodeBuilder(
          NavService02Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app02.permissions,
              transitions = listOf(tr<Nav02AppChildFinishRequest.Permissions>(Finish(Unit))),
            ),
            "app.permissions" to TestFlowNode(
              initialTarget = AbsoluteTarget(Path("app", "permissions", "intro")),
            ),
            "app.permissions.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      val ex = runCatching { sut.start() }.exceptionOrNull()
      ex shouldNotBe null
      (ex is IllegalStateException) shouldBe true
      (ex!!.message?.contains("AbsoluteTarget is not supported as FlowNode.initial") == true) shouldBe true
    }

    should("re-entrant sendEvent from inside a listener with no scheduler set drains in-loop preserving FIFO order") {
      // A3-1: re-entrant sendEvent calls while isDispatching == true must be appended to
      // _enqueuedEvents (sendEvent at NavigationService.kt:425-428) and then drained by the same
      // outer while-loop (lines 429-448). With no scheduler set, the drain happens in-loop and
      // listeners observe transitions in FIFO order — A first, then B, then C.
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(
                tr("A", Target.app05.main),
                tr("B", Target.app05.test),
                tr("C", Target.app05.intro),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      val states = mutableListOf<String>()
      sut.addTransitionListener { state ->
        states.add(state.active)
        // On the first transition for A → "app.main", re-enter twice — both must queue and drain
        // in FIFO order (B before C) so the final sequence is main → test → intro.
        if (state.active == "app.main") {
          sut.sendEvent(TestEvent("B"))
          sut.sendEvent(TestEvent("C"))
        }
      }
      sut.start() // delivers "app.intro" via initial listener replay
      sut.sendEvent(TestEvent("A")) // → "app.main", queues B, C; drains: → "app.test", → "app.intro"

      // All three transitions observed in FIFO order, payloads/state intact (active path
      // reflects the actual final node).
      states shouldBe listOf("app.intro", "app.main", "app.test", "app.intro")
    }

    should(
      "setEnqueuedEventsScheduler between two re-entrant events hands off second to scheduler and resumes when scheduler dispatches",
    ) {
      // A3-2: when a scheduler is set mid-listener, the outer drain loop at
      // NavigationService.kt:444-447 hands off the NEXT queued event to the scheduler and breaks
      // the loop, so no event is dropped. The user-supplied scheduler captures the event; manually
      // re-invoking sendEvent(captured) resumes processing — and because isDispatching has been
      // reset to false in the finally block, the re-entry now drains normally.
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(
                tr("A", Target.app05.main),
                tr("B", Target.app05.test),
                tr("C", Target.app05.intro),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      val scheduled = mutableListOf<Event>()
      val states = mutableListOf<String>()
      sut.addTransitionListener { state ->
        states.add(state.active)
        if (state.active == "app.main") {
          // Two re-entrant calls queued behind the current dispatch
          sut.sendEvent(TestEvent("B"))
          sut.sendEvent(TestEvent("C"))
          // Install scheduler AFTER first re-entrant landed — so the drain loop's
          // enqueuedEventScheduler?.let branch fires when popping the first queued event (B).
          sut.setEnqueuedEventsScheduler { evt -> scheduled.add(evt) }
        }
      }
      sut.start() // → "app.intro"
      sut.sendEvent(TestEvent("A")) // → "app.main"; queues B, C; scheduler captures B, breaks loop

      // After A: we observed intro + main. The scheduler captured B (the first queued event); C is
      // still sitting in _enqueuedEvents waiting to be drained when sendEvent re-runs.
      states shouldBe listOf("app.intro", "app.main")
      scheduled.size shouldBe 1
      (scheduled[0] is TestEvent && (scheduled[0] as TestEvent).name == "B") shouldBe true

      // Manually dispatch the captured event — this is the contract the scheduler must uphold.
      // The remaining queued event (C) is drained in the same outer loop after B's transition,
      // but again hits the scheduler and is captured.
      sut.sendEvent(scheduled[0])

      states shouldBe listOf("app.intro", "app.main", "app.test")
      scheduled.size shouldBe 2
      (scheduled[1] is TestEvent && (scheduled[1] as TestEvent).name == "C") shouldBe true

      // Dispatch the third captured event to finish the chain — no event was dropped.
      sut.sendEvent(scheduled[1])
      states shouldBe listOf("app.intro", "app.main", "app.test", "app.intro")
    }

    should("chain of N>=3 nested re-entrant sendEvent calls drains in FIFO with payloads intact") {
      // A3-3: a listener that re-enters sendEvent on every transition produces a chain of N
      // re-entrant calls. The single outer while-loop must drain them all in FIFO order (B then C
      // then D), each producing the expected destination — proving the queue is preserved across
      // the chain and no event is lost.
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(
                tr("A", Target.app05.main),
                tr("B", Target.app05.test),
                tr("C", Target.app05.intro),
                tr("D", Target.app05.main),
              ),
            ),
            "app.intro" to TestScreenNode(),
            "app.main" to TestScreenNode(),
            "app.test" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      val states = mutableListOf<String>()
      var reentryCount = 0
      // The follow-up sequence: after A (main), enqueue B → test; after B (test), enqueue C →
      // intro; after C (intro), enqueue D → main. Total: 4 transitions chained from a single
      // top-level sendEvent(A), each one re-entered from inside the listener.
      val followUp = mapOf(
        "app.main" to "B",
        "app.test" to "C",
        "app.intro" to "D",
      )
      sut.addTransitionListener { state ->
        states.add(state.active)
        if (states.size == 1) return@addTransitionListener // skip initial replay
        followUp[state.active]?.let { next ->
          if (reentryCount < 3) {
            reentryCount += 1
            sut.sendEvent(TestEvent(next))
          }
        }
      }
      sut.start() // → "app.intro" (counted as initial replay, no re-entry)
      sut.sendEvent(TestEvent("A"))
      // Chain: A → main → (B re-entered) → test → (C re-entered) → intro → (D re-entered) → main
      // All three re-entries happened inside the same outer drain-loop, FIFO-preserved.

      reentryCount shouldBe 3
      states shouldBe listOf("app.intro", "app.main", "app.test", "app.intro", "app.main")
    }

    should("one listener throwing during dispatch does not prevent other listeners from being notified") {
      // Listeners A, B, C registered (before start) in that order; B throws on every invocation.
      // Without per-listener try/catch, B's throw propagates out of the
      // `listeners.toList().forEach { it(state.copy()) }` loop and C is never notified.
      // With the fix, every listener still receives the state event and the FIRST thrown
      // exception propagates after all listeners ran.
      //
      // Listeners are registered BEFORE start() so the throwing branch exercised here is the
      // sendEvent dispatch loop (NavigationService.kt:674) — NOT the addTransitionListener
      // immediate-invoke path which is asserted by a separate existing test.
      val sut = NavigationService(
        TestNodeBuilder(
          NavService01Schema(),
          mapOf(
            "app" to TestFlowNode(initialTarget = Target.app01.intro),
            "app.intro" to TestScreenNode(),
          ),
        ),
        onFinishRequest = { _: Int -> Stay },
      )

      val deliveries = mutableListOf<String>()
      sut.addTransitionListener { _ -> deliveries.add("A") }
      sut.addTransitionListener { _ ->
        deliveries.add("B")
        throw RuntimeException("B explodes")
      }
      sut.addTransitionListener { _ -> deliveries.add("C") }

      val ex = runCatching { sut.start() }.exceptionOrNull()
      (ex is RuntimeException) shouldBe true
      ex?.message shouldBe "B explodes"
      // Critical: C must still have been notified even though B threw.
      deliveries shouldBe listOf("A", "B", "C")
    }

    should("NavigateTo with an empty targets list throws an IllegalArgumentException with a clear message") {
      val ex = runCatching { NavigateTo(emptyList()) }.exceptionOrNull()
      (ex is IllegalArgumentException) shouldBe true
      (ex?.message?.contains("at least one target") == true) shouldBe true
    }

    should("maybeResolveInitial Target/ScreenTarget arm detects cycle through visitedPaths set (R6 defensive guard)") {
      // R6: the Target overload's ScreenTarget arm used to return without adding targetPathAbs
      // to visitedPaths, so a future chain that re-arrives at the same absolute path through a
      // ScreenTarget hop would silently re-return instead of throwing. This test invokes
      // maybeResolveInitial directly with a pre-populated visitedPaths set containing the
      // target path — exercising the new cycle check in the ScreenTarget arm.
      //
      // Defensive guard: practical reach is low (standard FlowNode.initial chains terminate at
      // the first ScreenTarget hop), but the invariant — every visited absolute path is in
      // visitedPaths — is now uniform across all three arms.
      val nodeBuilder = TestNodeBuilder(
        NavService01Schema(),
        mapOf("app" to TestFlowNode(initialTarget = Target.app01.intro), "app.intro" to TestScreenNode()),
      )
      val visited = mutableSetOf<Path>(Path("app", "intro"))
      val ex = runCatching {
        maybeResolveInitial(
          target = ScreenTarget(Path("intro")),
          targetPathAbs = Path("app", "intro"),
          nodeBuilder = nodeBuilder,
          nodes = emptyMap(),
          schema = nodeBuilder.schema,
          payloads = mutableMapOf(),
          callingRegionId = RegionId(Path("app")),
          visitedPaths = visited,
        )
      }.exceptionOrNull()
      (ex is IllegalStateException) shouldBe true
      (ex?.message?.contains("cycle detected") == true) shouldBe true
    }

    // ── R9 (bug-hunt re-trace): throwing onExit must not block sibling onExit in prune ──────────
    // synchronizeNodes had two prune loops that called callOnExit inline without runCatching.
    // If one consumer's onExit threw, the iteration stopped — sibling nodes/regions in the same
    // prune sweep never received onExit. Real-world leak surface: a HomeNode tab switch fully
    // prunes the off-tab region (whole-region prune via the B1 loop) — if any node in that
    // region's onExit threw, deeper nodes' onExit (responsible for DI scope tear-down via
    // CoroutineScopeHooks) were silently skipped. cleanDispose already runCatching's each step;
    // normal-navigation prune now mirrors that pattern.
    should(
      "R9 per-region prune: throwing Node.onExit does not stop sibling onExit calls; primary " +
        "throw propagates with the rest attached via addSuppressed",
    ) {
      val exitedPaths = mutableListOf<String>()
      val sut = NavigationService(
        TestNodeBuilder(
          NavService06Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app06.intro,
              transitions = listOf(
                tr(on = "A", target = Target.app06.main),
                tr(on = "B", target = Target.app06.test),
                tr(on = "POP", target = Target.app06.intro),
              ),
            ),
            "app.intro" to TestScreenNode(onExitImpl = { exitedPaths.add("intro") }),
            "app.intro.main" to TestScreenNode(onExitImpl = { exitedPaths.add("main") }),
            // The DEEPEST screen throws. Iteration is reversed (deepest-first), so without R9
            // the throw here would stop the loop before `main`'s onExit could fire. With R9 the
            // runCatching wrap lets `main` still receive onExit; the throw is collected and
            // rethrown after the loop completes.
            "app.intro.main.test" to TestScreenNode(
              onExitImpl = {
                exitedPaths.add("test")
                error("test onExit throws")
              },
            ),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )

      var thrown: Throwable? = null
      sut.collectTransitions().test {
        awaitItem() // intro alive
        sut.sendEvent(TestEvent("A"))
        awaitItem() // main alive
        sut.sendEvent(TestEvent("B"))
        awaitItem() // test alive (stack: intro, main, test)
        thrown = runCatching { sut.sendEvent(TestEvent("POP")) }.exceptionOrNull()
        cancelAndIgnoreRemainingEvents()
      }

      // Both deeper screens (test + main) received onExit even though test (deepest, iterated
      // first via previousAlive.reversed()) threw. Without R9 the throw would stop the loop
      // immediately after the test entry, leaving main without an onExit call.
      exitedPaths.shouldContainInOrder("test", "main")
      // The throw from test.onExit propagates out of sendEvent.
      (thrown is IllegalStateException) shouldBe true
      (thrown?.message?.contains("test onExit throws") == true) shouldBe true
    }
  })
