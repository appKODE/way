package ru.kode.way.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import ru.kode.way.FlowTarget
import ru.kode.way.HistoryTarget
import ru.kode.way.MissingPayloadException
import ru.kode.way.NullPayload
import ru.kode.way.Path
import ru.kode.way.Schema
import ru.kode.way.ScreenTarget
import ru.kode.way.Segment
import ru.kode.way.TestEvent
import ru.kode.way.TestFlowNode
import ru.kode.way.TestFlowNodeWithResult
import ru.kode.way.TestScreenNode
import ru.kode.way.name

private class FlowGraph : Graph("DslFlow") {
  val root = flow<Unit>("root")
  val list = screen("list")
  val item = parameterizedScreen<Int?>("item")
  val edit = parameterizedFlow<String, String>("edit")
  val form = screen("form")

  init {
    root to list to item
    item to edit to form
  }
}

private class PermissionGraph : Graph("DslPermission") {
  val permission = parameterizedFlow<String, Boolean>("permission")
  val rationale = screen("rationale")

  init {
    permission to rationale
  }
}

private class MultiParentGraph : Graph("DslFanIn") {
  val root = flow<Unit>("root")
  val a = screen("a")
  val b = parameterizedScreen<Int>("b")
  val permission = include("permission", PermissionGraph().permission)

  init {
    root to a to permission
    root to b to permission
  }
}

private class HistGraph : Graph("DslHist") {
  val root = flow<Unit>("root")
  val home = screen("home")
  val shop = flow<Unit>("shop")
  val cart = screen("cart")
  val shopHistory = history("shopHistory")
  val shopDeepHistory = deepHistory("shopDeepHistory")

  init {
    root to home
    root to shop to cart
    shop to shopHistory
    shop to shopDeepHistory
  }
}

private class NestedParallelGraph : Graph("DslNest") {
  val main = flow<Unit>("main")
  val start = screen("start")
  val tabs = parallel<Unit>("tabs")
  val left = flow<Unit>("left")
  val leftScreen = screen("leftScreen")
  val inner = parameterizedParallel<Int, Unit>("inner")
  val right = flow<Unit>("right")
  val rightScreen = screen("rightScreen")
  val feed = include("feed", FlowGraph().root)
  val tabsHistory = deepHistory("tabsHistory")

  init {
    main to start
    main to tabs
    tabs to left to leftScreen
    left to inner
    inner to right to rightScreen
    inner to feed
    tabs to tabsHistory
  }
}

class GraphTest :
  ShouldSpec({
    should("build a schema with targets relative to the closest flow") {
      val graph = FlowGraph()
      val schema = graph.schema
      val region = schema.regions.single()

      schema.rootSegment shouldBe graph.root.segment
      graph.item.target(3) shouldBe ScreenTarget(Path(listOf(graph.list.segment, graph.item.segment)), 3)
      graph.item.target(null) shouldBe ScreenTarget(Path(listOf(graph.list.segment, graph.item.segment)), NullPayload)
      graph.edit.target("a") shouldBe
        FlowTarget(Path(listOf(graph.list.segment, graph.item.segment, graph.edit.segment)), "a")
      graph.form.target() shouldBe ScreenTarget(Path(graph.form.segment))

      val formPath = schema.target(region, graph.form.segment, rootSegmentAlias = null)!!
      formPath.toString() shouldBe "root.list.item.edit.form"
      schema.nodeType(region, formPath, null) shouldBe Schema.NodeType.Screen
      schema.isParameterized(region, formPath, null) shouldBe false
      val editPath = schema.target(region, graph.edit.segment, null)!!
      schema.nodeType(region, editPath, null) shouldBe Schema.NodeType.Flow
      schema.isParameterized(region, editPath, null) shouldBe true
      schema.target(region, Segment("form@Other"), null).shouldBeNull()
    }

    should("report the result of a finished child flow") {
      val graph = FlowGraph()
      val region = graph.schema.regions.single()
      val editPath = graph.schema.target(region, graph.edit.segment, null)!!
      val event = graph.schema.createChildFlowFinishRequestEvent(region, editPath, "saved")

      event.childFlowResult(graph.edit) shouldBe "saved"
      TestEvent("x").childFlowResult(graph.edit).shouldBeNull()
    }

    should("pass payloads to the factories and throw for a missing one") {
      val graph = FlowGraph()
      val payloads = mutableListOf<Any?>()
      val nodeBuilder = graph.nodeBuilder {
        flow(graph.root) { TestFlowNode(graph.list.target()) }
        screen(graph.list) { TestScreenNode() }
        screen(graph.item) { TestScreenNode(payload = it).also { _ -> payloads.add(it) } }
        flow(graph.edit) { TestFlowNodeWithResult(graph.form.target(), dismissResult = "", payload = it) }
        screen(graph.form) { TestScreenNode() }
      }
      val region = graph.schema.regions.single()
      val itemPath = graph.schema.target(region, graph.item.segment, null)!!
      val editPath = graph.schema.target(region, graph.edit.segment, null)!!

      nodeBuilder.build(itemPath, mapOf(itemPath to NullPayload), null)
      nodeBuilder.build(itemPath, mapOf(itemPath to 5), null)
      (nodeBuilder.build(editPath, mapOf(editPath to "e"), null) as TestFlowNodeWithResult<*>).payload shouldBe "e"
      payloads shouldBe listOf(null, 5)
      shouldThrow<MissingPayloadException> { nodeBuilder.build(editPath, emptyMap(), null) }.path shouldBe editPath
    }

    should("require a factory for every node") {
      val graph = FlowGraph()

      shouldThrow<IllegalArgumentException> {
        graph.nodeBuilder { flow(graph.root) { TestFlowNode(graph.list.target()) } }
      }.message shouldContain "list, item, edit, form"
      shouldThrow<IllegalArgumentException> {
        graph.nodeBuilder { screen(FlowGraph().list) { TestScreenNode() } }
      }.message shouldContain "does not belong"
    }

    should("reject an invalid graph") {
      fun invalid(build: () -> Graph) = shouldThrow<IllegalArgumentException> { build().schema }.message!!

      invalid {
        object : Graph("G") {
          val a = flow<Unit>("a")
          val b = flow<Unit>("b")
        }
      } shouldContain "must have one root"
      invalid {
        object : Graph("G") {
          val a = screen("a")
          val b = screen("b")

          init {
            a to b
          }
        }
      } shouldContain "must be a flow"
      invalid {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val a = screen("a")
          val b = screen("b")

          init {
            a to b
            b to a
          }
        }
      } shouldContain "cycle"
      invalid {
        object : Graph("G") {
          val r = flow<Unit>("r")
        }
      } shouldContain "a flow must have at least one child"
      invalid {
        object : Graph("G") {
          val p = parallel<Unit>("p")
          val s = screen("s")

          init {
            p to s
          }
        }
      } shouldContain "must be flows, parallel flows or included graphs"
      invalid {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val i = include("i", FlowGraph().root)
          val s = screen("s")

          init {
            r to i to s
          }
        }
      } shouldContain "can't have children"
      shouldThrow<IllegalArgumentException> {
        object : Graph("G") {
          val a = flow<Unit>("a")
          val b = screen("a")
        }
      }.message shouldContain "declared twice"
      invalid {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val a = screen("a")
          val b = screen("b")

          init {
            r to a
            r to b to a
          }
        }
      } shouldContain "several parents (fan-in): a, only an included graph can have several parents"
      shouldThrow<IllegalArgumentException> {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val a = screen("a")

          init {
            r to a
            r to a
          }
        }
      }.message shouldContain "is declared twice"
      shouldThrow<IllegalArgumentException> {
        object : Graph("G") {
          val i = include("i", FlowGraph().edit)
        }
      }.message shouldContain "'edit' is not the root of graph 'DslFlow'"
    }
    should("pass typed payloads to the factories, null for a missing payload of a nullable root") {
      val graph = object : Graph("Nullable") {
        val root = parameterizedFlow<String?, Unit>("root")
        val list = screen("list")

        init {
          root to list
        }
      }
      val payloads = mutableListOf<String?>()
      val nodeBuilder = graph.nodeBuilder {
        flow(graph.root) { payload: String? ->
          TestFlowNode(graph.list.target(), payload = payload.also(payloads::add))
        }
        screen(graph.list) { TestScreenNode() }
      }
      val region = graph.schema.regions.single()
      val rootPath = Path(graph.root.segment)

      graph.schema.isParameterized(region, rootPath, null) shouldBe false
      nodeBuilder.build(rootPath, emptyMap(), null)
      nodeBuilder.build(rootPath, mapOf(rootPath to NullPayload), null)
      nodeBuilder.build(rootPath, mapOf(rootPath to "a"), null)
      payloads shouldBe listOf(null, null, "a")
      graph.root.target(null) shouldBe FlowTarget(rootPath, NullPayload)
      FlowGraph().let { it.root.target() shouldBe FlowTarget(Path(it.root.segment)) }
    }

    should("pass the payload of an included graph to its factory and throw for a missing one") {
      val graph = MultiParentGraph()
      val included = mutableListOf<String>()
      val nodeBuilder = graph.nodeBuilder {
        flow(graph.root) { TestFlowNode(graph.a.target()) }
        screen(graph.a) { TestScreenNode() }
        screen(graph.b) { id: Int -> TestScreenNode(payload = id) }
        include(graph.permission) { reason: String ->
          included.add(reason)
          val permission = graph.permission.included as PermissionGraph
          permission.nodeBuilder {
            flow(permission.permission) { TestFlowNodeWithResult(permission.rationale.target(), false, payload = it) }
            screen(permission.rationale) { TestScreenNode() }
          }
        }
      }
      val path = Path(listOf(graph.root.segment, graph.a.segment, graph.permission.segment))

      val node = nodeBuilder.build(path, mapOf(path to "camera"), null) as TestFlowNodeWithResult<*>
      node.payload shouldBe "camera"
      included shouldBe listOf("camera")
      val otherPath = Path(listOf(graph.root.segment, graph.b.segment, graph.permission.segment))
      shouldThrow<MissingPayloadException> { nodeBuilder.build(otherPath, emptyMap(), null) }.path shouldBe otherPath
    }

    should("target the parent of a history node and leave it out of the schema") {
      val graph = HistGraph()
      val schema = graph.schema
      val region = schema.regions.single()
      val shopPath = Path(listOf(graph.root.segment, graph.shop.segment))

      graph.shopHistory.deep shouldBe false
      graph.shopHistory.target() shouldBe HistoryTarget(shopPath, deep = false)
      graph.shopDeepHistory.target() shouldBe HistoryTarget(shopPath, deep = true)
      graph.shopHistory.target(prefix = Path("app")) shouldBe
        HistoryTarget(Path(listOf(Segment("app"), graph.root.segment, graph.shop.segment)), deep = false)
      schema.target(region, graph.shopHistory.segment, null).shouldBeNull()
      schema.target(region, graph.cart.segment, null).toString() shouldBe "root.shop.cart"
      // history nodes are never built, so they need no factory
      graph.nodeBuilder {
        flow(graph.root) { TestFlowNode(graph.home.target()) }
        screen(graph.home) { TestScreenNode() }
        flow(graph.shop) { TestFlowNode(graph.cart.target()) }
        screen(graph.cart) { TestScreenNode() }
      }
    }

    should("reject a history node out of place") {
      fun invalid(build: () -> Graph) = shouldThrow<IllegalArgumentException> { build().schema }.message!!

      invalid {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val s = screen("s")
          val h = history("h")

          init {
            r to s
          }
        }
      } shouldContain "history node 'h' of graph 'G' has no parent flow"
      invalid {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val h = history("h")
          val s = screen("s")

          init {
            r to h to s
          }
        }
      } shouldContain "must be a leaf, but has children: s"
      invalid {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val s = screen("s")
          val h = history("h")

          init {
            r to s to h
          }
        }
      } shouldContain "its parent 's' is not"
      invalid {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val h = history("h")
          val i = include("i", FlowGraph().root)

          init {
            r to i to h
          }
        }
      } shouldContain "history under an included graph or a screen is not supported"
      invalid {
        object : Graph("G") {
          val p = parallel<Unit>("p")
          val f = flow<Unit>("f")
          val s = screen("s")
          val h = history("h")

          init {
            p to f to s
            p to h
          }
        }
      } shouldContain "is under the parallel 'p' with no flow above it"
    }

    should("build regions and sub-schemas of nested parallel flows") {
      val graph = NestedParallelGraph()
      val schema = graph.schema

      schema.regions.map { it.path.toString() } shouldContainExactly
        listOf("main", "main.tabs.left", "main.tabs.left.inner.right", "main.tabs.left.inner.feed")
      schema.childSchemas.keys.map { it.name } shouldContainExactlyInAnyOrder
        listOf("tabs", "left", "inner", "right", "feed")
      schema.childSchemas.getValue(graph.tabs.segment).regions.map { it.path.toString() } shouldContainExactly
        listOf("tabs.left", "tabs.left.inner.right", "tabs.left.inner.feed")
      schema.childSchemas.getValue(graph.inner.segment).regions.map { it.path.toString() } shouldContainExactly
        listOf("inner.right", "inner.feed")

      val region = schema.regions.first()
      val innerPath = schema.target(region, graph.inner.segment, null)!!
      innerPath.toString() shouldBe "main.tabs.left.inner"
      schema.nodeType(region, innerPath, null) shouldBe Schema.NodeType.ParallelFlow
      schema.isParameterized(region, innerPath, null) shouldBe true
      graph.inner.target(3) shouldBe FlowTarget(Path(graph.inner.segment), 3)
      graph.tabsHistory.target() shouldBe
        HistoryTarget(Path(listOf(graph.main.segment, graph.tabs.segment)), deep = true)
    }

    should("give the region id of a parallel's child relative to the parallel's schema") {
      val graph = NestedParallelGraph()
      val tabsRegions = graph.schema.childSchemas.getValue(graph.tabs.segment).regions
      val innerRegions = graph.schema.childSchemas.getValue(graph.inner.segment).regions

      graph.tabs.regionId(graph.left) shouldBe tabsRegions.first()
      graph.inner.regionId(graph.right) shouldBe innerRegions[0]
      graph.inner.regionId(graph.feed) shouldBe innerRegions[1]
      shouldThrow<IllegalArgumentException> { graph.tabs.regionId(graph.right) }.message shouldContain
        "'right' is not a region of parallel 'tabs'"
    }

    should("reject a parallel flow without children") {
      fun invalid(build: () -> Graph) = shouldThrow<IllegalArgumentException> { build().schema }.message!!

      invalid {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val p = parallel<Unit>("p")

          init {
            r to p
          }
        }
      } shouldContain "parallel 'p' of graph 'G' has no children"
      invalid {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val p = parallel<Unit>("p")
          val h = history("h")

          init {
            r to p to h
          }
        }
      } shouldContain "parallel 'p' of graph 'G' has no children"
    }

    should("give every path of an included graph with several parents") {
      val graph = MultiParentGraph()
      val schema = graph.schema
      val region = schema.regions.single()

      schema.targets(region, graph.permission.segment, null).map { it.toString() } shouldContainExactly
        listOf("root.a.permission", "root.b.permission")
      graph.permission.target("camera", via = graph.a) shouldBe
        FlowTarget(Path(listOf(graph.a.segment, graph.permission.segment)), "camera")
      graph.permission.target("camera", via = graph.b) shouldBe
        FlowTarget(Path(listOf(graph.b.segment, graph.permission.segment)), "camera")
      shouldThrow<IllegalArgumentException> { graph.permission.target("camera") }.message shouldContain
        "pass the one to enter it from as `via`"
      shouldThrow<IllegalArgumentException> {
        graph.permission.target("camera", via = graph.root)
      }.message shouldContain
        "'root' is not a parent of 'permission'"
    }

    should("reject several parents out of place") {
      fun invalid(build: () -> Graph) = shouldThrow<IllegalArgumentException> { build().schema }.message!!

      invalid {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val a = screen("a")
          val b = screen("b")
          val f = flow<Unit>("f")
          val s = screen("s")

          init {
            r to a to f to s
            r to b to f
          }
        }
      } shouldContain "only an included graph can have several parents"
      invalid {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val p = parallel<Unit>("p")
          val f = flow<Unit>("f")
          val s = screen("s")
          val i = include("i", FlowGraph().root)

          init {
            r to p to f to s to i
            p to i
          }
        }
      } shouldContain "has several parents, one of which is the parallel 'p'"
      invalid {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val p = parallel<Unit>("p")
          val f1 = flow<Unit>("f1")
          val s1 = screen("s1")
          val f2 = flow<Unit>("f2")
          val s2 = screen("s2")
          val i = include("i", FlowGraph().root)

          init {
            r to p to f1 to s1 to i
            p to f2 to s2 to i
          }
        }
      } shouldContain "has parents in different parallel regions (s1, s2)"
      // parents in one region of a parallel flow are fine
      object : Graph("G") {
        val r = flow<Unit>("r")
        val p = parallel<Unit>("p")
        val f = flow<Unit>("f")
        val s1 = screen("s1")
        val s2 = screen("s2")
        val i = include("i", FlowGraph().root)

        init {
          r to p to f to s1 to i
          f to s2 to i
        }
      }.schema.regions.map { it.path.toString() } shouldContainExactly listOf("r", "r.p.f")
    }
  })
