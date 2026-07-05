package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import ru.kode.way.par02.Par02AppNodeBuilder
import ru.kode.way.par02.Parallel02Schema
import ru.kode.way.par02.alpha.Par02AlphaNodeBuilder
import ru.kode.way.par02.alpha.Parallel02AlphaSchema
import ru.kode.way.par02.alpha.par02Alpha
import ru.kode.way.par02.beta.Par02BetaNodeBuilder
import ru.kode.way.par02.beta.Parallel02BetaSchema
import ru.kode.way.par02.beta.par02Beta
import ru.kode.way.par02.main.Par02MainNodeBuilder
import ru.kode.way.par02.main.Parallel02MainSchema
import ru.kode.way.par02.par02App

/**
 * Focused coverage for the [NodeBuilder.invalidateCache] contract introduced.
 *
 * The runtime calls [NodeBuilder.invalidateCache] once per transition with the union of every
 * region's alive paths (see `NavigationService.kt:398-408`). The generated parent NodeBuilder
 * then propagates the call to each cached child builder, but only after:
 *   1. Filtering [alivePaths] to those that descend through the cached child's `builderPath`.
 *   2. Rebasing each surviving path by dropping `builderPath.length - 1` leading segments — so
 *      the child receives paths in its own schema-relative coordinate system, not the global one.
 *
 * The codegen is in `NodeBuilderCodegen.kt:293-318`; the generated implementation in
 * `Par02MainNodeBuilder.kt:65-72` is the canonical example.
 */
class InvalidateCacheTest : ShouldSpec() {
  init {
    should("invalidateCache receives subtree-filtered alivePaths with prefix dropped by builderPath.length-1") {
      // Wrap the alpha leaf NodeBuilder so every invalidateCache call is recorded. The leaf's
      // own invalidateCache is a no-op (see Par02AlphaNodeBuilder.kt:37), but the parent
      // Par02MainNodeBuilder MUST still call it with a rebased subtree (paths starting with
      // `par02Alpha`, not the global set that contains `par02App`/`par02Main` ancestors).
      val recordedAlpha = mutableListOf<Set<Path>>()
      val alphaCalls = makeAlphaNodeBuilder().recordingWrapper { recordedAlpha.add(it) }
      val recordedBeta = mutableListOf<Set<Path>>()
      val betaCalls = makeBetaNodeBuilder().recordingWrapper { recordedBeta.add(it) }

      val sut = buildPar02ServiceWith(alphaCalls, betaCalls)

      sut.collectTransitions().test {
        awaitItem() // initial — both regions entered, first invalidateCache sweep fired

        recordedAlpha.shouldNotBeEmptySafe()
        recordedBeta.shouldNotBeEmptySafe()

        // Subtree-filter + rebase check: every path the alpha child received must start with
        // its own root segment (`par02Alpha`) and MUST NOT carry the parent ancestors
        // (`par02App`, `par02Main`). If the parent forwarded the raw global set, the first
        // segment names would be `par02App` and the assertion would fail.
        recordedAlpha.forEach { set ->
          set.forEach { path ->
            path.firstSegment().name shouldBe "par02Alpha"
            // Negative-verify: no leaked ancestor segments in any received path.
            path.segments.none { it.name == "par02App" } shouldBe true
            path.segments.none { it.name == "par02Main" } shouldBe true
          }
        }
        recordedBeta.forEach { set ->
          set.forEach { path ->
            path.firstSegment().name shouldBe "par02Beta"
            path.segments.none { it.name == "par02App" } shouldBe true
            path.segments.none { it.name == "par02Main" } shouldBe true
          }
        }

        // Exact-set check on the most recent (post-init) sweep: alpha is alive at
        // par02App.par02Main.par02Alpha + par02App.par02Main.par02Alpha.par02AlphaScreen1
        // (the initial cascade). After the parent's drop(builderPath.length - 1) the alpha
        // child must receive EXACTLY the schema-relative pair.
        val lastAlphaSet = recordedAlpha.last().map { it.toString() }
        lastAlphaSet.shouldContainExactlyInAnyOrder("par02Alpha", "par02Alpha.par02AlphaScreen1")

        val lastBetaSet = recordedBeta.last().map { it.toString() }
        lastBetaSet.shouldContainExactlyInAnyOrder("par02Beta", "par02Beta.par02BetaScreen1")

        cancelAndIgnoreRemainingEvents()
      }
    }

    should("empty alivePaths set evicts everything from the cached NodeBuilders map") {
      // Drive Par02MainNodeBuilder.build() to populate its internal `nodeBuilders` cache, then
      // call invalidateCache(emptySet()) and prove eviction happened by observing the create
      // counters: a subsequent build() must re-create the child NodeBuilders (cache miss).
      var alphaCreates = 0
      var betaCreates = 0
      val alphaBuilder = makeAlphaNodeBuilder()
      val betaBuilder = makeBetaNodeBuilder()
      val mainBuilder = Par02MainNodeBuilder(
        nodeFactory = object : Par02MainNodeBuilder.Factory {
          override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
          override fun createPar02AlphaNodeBuilder(): NodeBuilder {
            alphaCreates++
            return alphaBuilder
          }
          override fun createPar02BetaNodeBuilder(): NodeBuilder {
            betaCreates++
            return betaBuilder
          }
        },
        schema = Parallel02MainSchema(Parallel02AlphaSchema(), Parallel02BetaSchema()),
      )

      // Populate the cache: build() both child subtrees so the internal map has both keys.
      val mainRoot = Segment("par02Main@Parallel02Main:src/commonTest/way/parallel-test02-main.dot")
      val alphaSeg = Segment("par02Alpha@Parallel02Alpha:src/commonTest/way/parallel-test02-alpha.dot")
      val betaSeg = Segment("par02Beta@Parallel02Beta:src/commonTest/way/parallel-test02-beta.dot")
      mainBuilder.build(Path(listOf(mainRoot, alphaSeg)), payloads = emptyMap(), rootSegmentAlias = mainRoot)
      mainBuilder.build(Path(listOf(mainRoot, betaSeg)), payloads = emptyMap(), rootSegmentAlias = mainRoot)
      alphaCreates shouldBe 1
      betaCreates shouldBe 1

      // Empty alivePaths → retainAll keeps nothing (no key is a prefix of any path in an empty
      // set). The map is now empty; the next build() must hit the factory again for both
      // children.
      mainBuilder.invalidateCache(emptySet())

      mainBuilder.build(Path(listOf(mainRoot, alphaSeg)), payloads = emptyMap(), rootSegmentAlias = mainRoot)
      mainBuilder.build(Path(listOf(mainRoot, betaSeg)), payloads = emptyMap(), rootSegmentAlias = mainRoot)
      alphaCreates shouldBe 2
      betaCreates shouldBe 2
    }

    // Short-form regression guard for Way, written directly against the new
    // `Set<Path>` signature of [NodeBuilder.invalidateCache] (the long-form lives in
    // `ParallelNodeTest.kt` at "invalidateCache does not evict NodeBuilders that are alive
    // in sibling parallel regions" — that one drives a real transition through
    // `NavigationService`; this one validates the contract at the NodeBuilder layer in
    // isolation, so a regression in the codegen's retain predicate surfaces without
    // needing the full runtime).
    //
    // invalidateCache was called once PER REGION with that region's active
    // path; the generated `retainAll { key -> alivePaths.any { it.startsWith(key) } }`
    // (now `Set<Path>`) evicted siblings on every transition because no key in the cache
    // was a prefix of the single-region set. Post-fix: pass the UNION of all regions'
    // alive paths and the same retainAll keeps both siblings.
    should("cross-region retain: both siblings kept when union passed") {
      var alphaCreates = 0
      var betaCreates = 0
      val mainBuilder = Par02MainNodeBuilder(
        nodeFactory = object : Par02MainNodeBuilder.Factory {
          override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
          override fun createPar02AlphaNodeBuilder(): NodeBuilder {
            alphaCreates++
            return makeAlphaNodeBuilder()
          }
          override fun createPar02BetaNodeBuilder(): NodeBuilder {
            betaCreates++
            return makeBetaNodeBuilder()
          }
        },
        schema = Parallel02MainSchema(Parallel02AlphaSchema(), Parallel02BetaSchema()),
      )
      val mainRoot = Segment("par02Main@Parallel02Main:src/commonTest/way/parallel-test02-main.dot")
      val alphaSeg = Segment("par02Alpha@Parallel02Alpha:src/commonTest/way/parallel-test02-alpha.dot")
      val betaSeg = Segment("par02Beta@Parallel02Beta:src/commonTest/way/parallel-test02-beta.dot")
      val alphaScreen2 = Segment("par02AlphaScreen2@Parallel02Alpha:src/commonTest/way/parallel-test02-alpha.dot")
      val betaScreen1 = Segment("par02BetaScreen1@Parallel02Beta:src/commonTest/way/parallel-test02-beta.dot")

      // Populate the cache for both siblings.
      mainBuilder.build(Path(listOf(mainRoot, alphaSeg)), payloads = emptyMap(), rootSegmentAlias = mainRoot)
      mainBuilder.build(Path(listOf(mainRoot, betaSeg)), payloads = emptyMap(), rootSegmentAlias = mainRoot)
      alphaCreates shouldBe 1
      betaCreates shouldBe 1

      // Both child builderPaths must be retained because
      // each is a prefix of at least one path in the union.
      val union = setOf(
        Path(listOf(mainRoot, alphaSeg, alphaScreen2)),
        Path(listOf(mainRoot, betaSeg, betaScreen1)),
      )
      mainBuilder.invalidateCache(union)

      // Next build() for each child must be a cache hit — counters stay at 1.
      mainBuilder.build(Path(listOf(mainRoot, alphaSeg)), payloads = emptyMap(), rootSegmentAlias = mainRoot)
      mainBuilder.build(Path(listOf(mainRoot, betaSeg)), payloads = emptyMap(), rootSegmentAlias = mainRoot)
      alphaCreates shouldBe 1
      betaCreates shouldBe 1
    }
  }
}

// region — fixtures

private fun makeAlphaNodeBuilder(): NodeBuilder = Par02AlphaNodeBuilder(
  nodeFactory = object : Par02AlphaNodeBuilder.Factory {
    override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par02Alpha.par02AlphaScreen1)
    override fun createPar02AlphaScreen1Node(): ScreenNode = TestScreenNode()
    override fun createPar02AlphaScreen2Node(): ScreenNode = TestScreenNode()
  },
  schema = Parallel02AlphaSchema(),
)

private fun makeBetaNodeBuilder(): NodeBuilder = Par02BetaNodeBuilder(
  nodeFactory = object : Par02BetaNodeBuilder.Factory {
    override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par02Beta.par02BetaScreen1)
    override fun createPar02BetaScreen1Node(): ScreenNode = TestScreenNode()
  },
  schema = Parallel02BetaSchema(),
)

/**
 * Wrap a NodeBuilder so every `invalidateCache(alivePaths)` invocation is captured via [record]
 * (the underlying call is forwarded unchanged). Lets a test assert exactly what set the parent
 * NodeBuilder propagated to the child — the source of truth for "is the subtree filter + rebase
 * working correctly?".
 */
private fun NodeBuilder.recordingWrapper(record: (Set<Path>) -> Unit): NodeBuilder {
  val delegate = this
  return object : NodeBuilder {
    override val schema: Schema = delegate.schema
    override fun build(path: Path, payloads: Map<Path, Any>, rootSegmentAlias: Segment?): Node =
      delegate.build(path, payloads, rootSegmentAlias)

    override fun invalidateCache(alivePaths: Set<Path>) {
      record(alivePaths)
      delegate.invalidateCache(alivePaths)
    }
  }
}

private fun buildPar02ServiceWith(alphaBuilder: NodeBuilder, betaBuilder: NodeBuilder): NavigationService<Unit> {
  val mainBuilder = Par02MainNodeBuilder(
    nodeFactory = object : Par02MainNodeBuilder.Factory {
      override fun createRootNode(): ParallelFlowNode<Unit> = TestParallelNode()
      override fun createPar02AlphaNodeBuilder(): NodeBuilder = alphaBuilder
      override fun createPar02BetaNodeBuilder(): NodeBuilder = betaBuilder
    },
    schema = Parallel02MainSchema(Parallel02AlphaSchema(), Parallel02BetaSchema()),
  )
  val appBuilder = Par02AppNodeBuilder(
    nodeFactory = object : Par02AppNodeBuilder.Factory {
      override fun createRootNode(): FlowNode<*> = TestFlowNode(initialTarget = Target.par02App.par02Main)
      override fun createPar02MainNodeBuilder(): NodeBuilder = mainBuilder
    },
    schema = Parallel02Schema(
      par02MainSchema = Parallel02MainSchema(Parallel02AlphaSchema(), Parallel02BetaSchema()),
    ),
  )
  return NavigationService(nodeBuilder = appBuilder, onFinishRequest = { Ignore })
}

private fun <T> List<T>.shouldNotBeEmptySafe() {
  (this.isNotEmpty()) shouldBe true
}

// endregion
