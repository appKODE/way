package ru.kode.way.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import ru.kode.way.FlowTarget
import ru.kode.way.MissingPayloadException
import ru.kode.way.NullPayload
import ru.kode.way.Path
import ru.kode.way.Schema
import ru.kode.way.ScreenTarget
import ru.kode.way.Segment
import ru.kode.way.TestEvent
import ru.kode.way.TestFlowNode
import ru.kode.way.TestScreenNode

private class FlowGraph : Graph("DslFlow") {
  val root = flow<Unit>("root")
  val list = screen("list")
  val item = parameterizedScreen<Int?>("item")
  val edit = flow<String>("edit", parameterized = true)
  val form = screen("form")

  init {
    root to list to item
    item to edit to form
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
        flow(graph.edit) { TestFlowNode(graph.form.target(), payload = it) }
        screen(graph.form) { TestScreenNode() }
      }
      val region = graph.schema.regions.single()
      val itemPath = graph.schema.target(region, graph.item.segment, null)!!
      val editPath = graph.schema.target(region, graph.edit.segment, null)!!

      nodeBuilder.build(itemPath, mapOf(itemPath to NullPayload), null)
      nodeBuilder.build(itemPath, mapOf(itemPath to 5), null)
      (nodeBuilder.build(editPath, mapOf(editPath to "e"), null) as TestFlowNode).payload shouldBe "e"
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
          val p = parallel("p")

          init {
            r to p
          }
        }
      } shouldContain "must be the root"
      invalid {
        object : Graph("G") {
          val p = parallel("p")
          val f = flow<Unit>("f")

          init {
            p to f
          }
        }
      } shouldContain "must be included graphs"
      invalid {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val i = include<Unit>("i", FlowGraph())
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
      shouldThrow<IllegalArgumentException> {
        object : Graph("G") {
          val r = flow<Unit>("r")
          val a = screen("a")
          val b = screen("b")

          init {
            r to a
            b to a
          }
        }
      }.message shouldContain "several parents"
    }
  })
