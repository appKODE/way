package ru.kode.way.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

private fun local(id: String) = Node.Flow.Local(id, "kotlin.Unit", null)
private fun parallel(id: String) = Node.Flow.LocalParallel(id, "kotlin.Unit", null)
private fun imported(id: String) = Node.Flow.Imported(id, "kotlin.Unit", null)
private fun screen(id: String) = Node.Screen(id, null)

class AdjacencyListTest :
  ShouldSpec({
    should("region roots of a plain flow graph is the single root flow") {
      val app = local("app")
      val screen1 = screen("screen1")
      val adjacencyList: AdjacencyList = mapOf(
        app to listOf(screen1),
        screen1 to emptyList(),
      )

      buildRegionRoots(adjacencyList).shouldContainExactlyInAnyOrder(app)
    }

    should("region roots of a parallel-rooted graph are the root parallel's children") {
      val main = parallel("main")
      val one = local("one")
      val two = local("two")
      val adjacencyList: AdjacencyList = mapOf(
        main to listOf(one, two),
        one to listOf(screen("oneScreen")),
        two to listOf(screen("twoScreen")),
      )

      buildRegionRoots(adjacencyList).shouldContainExactlyInAnyOrder(one, two)
    }

    should("region roots of a parallel-in-parallel graph are only the outermost parallel's children") {
      val main = parallel("main")
      val one = parallel("one")
      val two = local("two")
      val alpha = local("alpha")
      val beta = local("beta")
      val adjacencyList: AdjacencyList = mapOf(
        main to listOf(one, two),
        one to listOf(alpha, beta),
        two to listOf(screen("twoScreen")),
        alpha to listOf(screen("alphaScreen")),
        beta to listOf(screen("betaScreen")),
      )

      // `one` and `two` are the top parallel's regions; `alpha`/`beta` belong to `one`'s own
      // virtual sub-schema and must NOT surface as top-level region roots.
      buildRegionRoots(adjacencyList).shouldContainExactlyInAnyOrder(one, two)
    }

    should("a parallel reached through a Local flow surfaces a new flat region tier (acme-tabs shape)") {
      // Mirrors the REAL, runtime-validated `parallel-test-acme-tabs.dot`:
      //   acmeAppFlow[parallelFlow] -> acmeMainFlow[flow] -> acmeTabsFlow[parallelFlow] -> home/explore
      //   acmeAppFlow[parallelFlow] -> acmeAuthFlow[flow]
      // `acmeTabsFlow` is a parallel reached through a LOCAL flow (`acmeMainFlow`), so it starts a NEW
      // region tier: `acmeHomeTab`/`acmeExploreTab` are FLAT region roots alongside the top parallel's own
      // children `acmeMainFlow`/`acmeAuthFlow`. All four are top-level regions because the runtime region
      // model is FLAT — `NavigationService.materializeRegion` creates one top-level Region per `schema.regions`
      // entry keyed by absolute path. This is asserted directly by the runtime contract test
      // "acme-style layout: each region has its own absolute regionId.path anchored at acmeAppFlow"
      // in `ParallelNodeTest` (acmeAppFlow → exactly these four regions); a 2-region result would
      // FAIL it and break navigation. See buildRegionRoots' KDoc for the full flat-model rationale.
      val acmeAppFlow = parallel("acmeAppFlow")
      val acmeMainFlow = local("acmeMainFlow")
      val acmeAuthFlow = local("acmeAuthFlow")
      val acmeTabsFlow = parallel("acmeTabsFlow")
      val acmeHomeTab = local("acmeHomeTab")
      val acmeExploreTab = local("acmeExploreTab")
      val adjacencyList: AdjacencyList = mapOf(
        acmeAppFlow to listOf(acmeMainFlow, acmeAuthFlow),
        acmeMainFlow to listOf(acmeTabsFlow),
        acmeAuthFlow to listOf(screen("acmeAuthScreen")),
        acmeTabsFlow to listOf(acmeHomeTab, acmeExploreTab),
        acmeHomeTab to listOf(screen("acmeHomeScreen")),
        acmeExploreTab to listOf(screen("acmeExploreScreen")),
      )

      buildRegionRoots(adjacencyList)
        .shouldContainExactlyInAnyOrder(acmeMainFlow, acmeAuthFlow, acmeHomeTab, acmeExploreTab)
    }

    should("virtual sub-schema constructor param order matches call-site arg order (positional forwarding is safe)") {
      // SchemaCodegen forwards a virtual sub-schema's Imported flows POSITIONALLY:
      //   `VirtualSchema(prop1, prop2, ...)`
      // where the args come from `dfs(subAdjList, root)` (call site) and the virtual schema's own
      // constructor params come from `buildConstructorParameters(subAdjList)` — i.e. `subAdjList`
      // map iteration order. This test pins the invariant that makes that positional forwarding
      // correct: `subgraphFor` builds `subAdjList` as a LinkedHashMap in DFS order, so its map
      // order and a fresh `dfs(subAdjList, root)` over it visit Imported flows in the SAME order.
      val root = parallel("root")
      val a = imported("a")
      val b = local("b")
      val c = imported("c")
      val d = imported("d")
      val full: AdjacencyList = mapOf(
        root to listOf(a, b),
        a to emptyList(),
        b to listOf(c, d),
        c to emptyList(),
        d to emptyList(),
      )

      val subAdjList = full.subgraphFor(root)

      // Order the virtual schema's constructor would declare its params in (map iteration order).
      val constructorParamOrder = subAdjList.keys.filterIsInstance<Node.Flow.Imported>()
      // Order SchemaCodegen forwards the args in (fresh DFS over the subgraph).
      val callSiteArgOrder = mutableListOf<Node.Flow.Imported>()
      dfs(subAdjList, subAdjList.findRootNode()) { n -> if (n is Node.Flow.Imported) callSiteArgOrder.add(n) }

      callSiteArgOrder shouldBe constructorParamOrder
    }
  })
