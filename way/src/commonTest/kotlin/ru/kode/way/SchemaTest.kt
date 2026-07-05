package ru.kode.way

import app.cash.turbine.test
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import ru.kode.way.nav05.NavService05Schema
import ru.kode.way.nav05.app as app05

class SchemaTest :
  ShouldSpec({
    should("regionByName matches schema region by the final segment name, stripping the @file disambiguator") {
      val schema = object : Schema {
        override val rootSegment: Segment = Segment("home@home_flow.dot")
        override val childSchemas: Map<Segment, Schema> = emptyMap()
        override val regions: List<RegionId> = listOf(
          RegionId(Path(listOf(Segment("home@home_flow.dot"), Segment("exploreFlow@home_flow.dot")))),
          RegionId(Path(listOf(Segment("home@home_flow.dot"), Segment("myAcmeFlow@home_flow.dot")))),
          RegionId(Path(listOf(Segment("home@home_flow.dot"), Segment("profileFlow@home_flow.dot")))),
        )
        override fun target(regionId: RegionId, segment: Segment, rootSegmentAlias: Segment?): Path? = null
        override fun nodeType(regionId: RegionId, path: Path, rootSegmentAlias: Segment?): Schema.NodeType =
          Schema.NodeType.Flow
        override fun createChildFlowFinishRequestEvent(regionId: RegionId, path: Path, result: Any): Event =
          error("not used")
      }
      schema.regionByName("exploreFlow") shouldBe regions(schema, 0)
      schema.regionByName("myAcmeFlow") shouldBe regions(schema, 1)
      schema.regionByName("profileFlow") shouldBe regions(schema, 2)
      schema.regionByName("nonexistent") shouldBe null
      // Name comparison ignores the @file suffix — same module's regions are uniquely identified
      // by their pre-@ portion.
      schema.regionByName("exploreFlow") shouldNotBe null
    }

    // A9: validateSchema=true + NodeBuilder returns wrong-typed node — transition is rolled back
    // with a useful message naming path + expected vs actual type.
    // Schema (NavService05) declares "app.test" as Screen. The TestNodeBuilder below returns a
    // FlowNode at "app.test" instead. checkSchemaValidity (NavigationService.kt:308-337) is
    // expected to throw IllegalStateException via check(...) with the message
    // "according to schema, \"$path\" should be a $nodeType, but it is a ${FlowNode::class.simpleName}".
    // The outer transition catch (NavigationService.kt:283-301) then restores regionSnapshot,
    // enqueuedEventsSnapshot, and payloadsSnapshot — so the active path stays at "app.intro".
    should("validateSchema=true: wrong-typed node throws IllegalStateException with path/types and rolls back state") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(tr("A", Target.app05.test)),
            ),
            "app.intro" to TestScreenNode(),
            // WRONG TYPE: schema declares app.test as Screen, but we return a FlowNode here.
            "app.test" to TestFlowNode(initialTarget = Target.app05.intro),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      // Default is true, but make the contract explicit for this test.
      sut.validateSchema = true

      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.intro"

        val thrown = shouldThrow<IllegalStateException> {
          sut.sendEvent(TestEvent("A"))
        }

        // Message must name the offending path AND mention the schema-declared type
        // (Screen) and the actual node type (FlowNode). Per checkSchemaValidity (line 317):
        // "according to schema, \"$path\" should be a $nodeType, but it is a ${FlowNode::class.simpleName}"
        val message = thrown.message ?: ""
        message shouldContain "according to schema"
        message shouldContain "app.test"
        message shouldContain "Screen"
        message shouldContain "FlowNode"

        // Rollback: outer catch (NavigationService.kt:283-301) restored regionSnapshot, so the
        // active path is still the pre-transition app.intro. No state leak from the failed
        // transition.
        cancelAndIgnoreRemainingEvents()
      }

      // After the failed sendEvent, the service state should still reflect the pre-transition
      // configuration. We re-collect to take a fresh snapshot.
      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.intro"
        cancelAndIgnoreRemainingEvents()
      }
    }

    // A9: validateSchema=false + same wrong-typed node — checkSchemaValidity does not run; assert
    // NO premature exception from checkSchemaValidity. The gate at NavigationService.kt:271
    // ("if (validateSchema) checkSchemaValidity(...)") must be honored. Even if some other failure
    // occurs, the failure's message must NOT mention checkSchemaValidity's signature wording
    // ("according to schema").
    should("validateSchema=false: wrong-typed node does NOT trigger checkSchemaValidity") {
      val sut = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(tr("A", Target.app05.test)),
            ),
            "app.intro" to TestScreenNode(),
            // Same WRONG TYPE as the previous test, but checkSchemaValidity is disabled below.
            "app.test" to TestFlowNode(initialTarget = Target.app05.intro),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      sut.validateSchema = false

      sut.collectTransitions().test {
        awaitItem().active shouldBe "app.intro"

        // With validateSchema=false, checkSchemaValidity is skipped entirely. The transition
        // is expected to complete without raising an exception from the schema-validation path.
        shouldNotThrowAny {
          sut.sendEvent(TestEvent("A"))
        }

        cancelAndIgnoreRemainingEvents()
      }

      // Belt-and-suspenders: even if a future change made sendEvent throw here for an unrelated
      // reason, the failure must not be the schema-validity message. We capture the throwable and
      // check its message does not contain the checkSchemaValidity wording.
      val sut2 = NavigationService(
        TestNodeBuilder(
          NavService05Schema(),
          mapOf(
            "app" to TestFlowNode(
              initialTarget = Target.app05.intro,
              transitions = listOf(tr("A", Target.app05.test)),
            ),
            "app.intro" to TestScreenNode(),
            "app.test" to TestFlowNode(initialTarget = Target.app05.intro),
          ),
        ),
        onFinishRequest = { _: Unit -> Stay },
      )
      sut2.validateSchema = false

      sut2.collectTransitions().test {
        awaitItem().active shouldBe "app.intro"
        val caught: Throwable? = runCatching { sut2.sendEvent(TestEvent("A")) }.exceptionOrNull()
        // Either no exception (preferred) or — if one occurs — its message must not be the
        // signature checkSchemaValidity wording ("according to schema").
        if (caught != null) {
          (caught.message ?: "") shouldNotContain "according to schema"
        }
        cancelAndIgnoreRemainingEvents()
      }
    }
  })

private fun regions(schema: Schema, index: Int): RegionId = schema.regions[index]
