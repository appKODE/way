package ru.kode.way

import app.cash.turbine.test
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldContainOnly
import ru.kode.way.par01.Par01AppNodeBuilder
import ru.kode.way.par01.Parallel01Schema
import ru.kode.way.par01.bottom.Par01BottomNodeBuilder
import ru.kode.way.par01.bottom.Parallel01BottomSchema
import ru.kode.way.par01.bottom.par01Bottom
import ru.kode.way.par01.main.Par01MainNodeBuilder
import ru.kode.way.par01.main.Parallel01MainSchema
import ru.kode.way.par01.par01App
import ru.kode.way.par01.top.Par01TopNodeBuilder
import ru.kode.way.par01.top.Parallel01TopSchema
import ru.kode.way.par01.top.par01Top

class ParallelNodeTest : ShouldSpec() {
  init {
    should("resolve initial state with basic parallel setup") {
      val appSchema = Parallel01Schema(
        par01MainSchema = Parallel01MainSchema(
          par01TopSchema = Parallel01TopSchema(),
          par01BottomSchema = Parallel01BottomSchema()
        )
      )
      val topNodeBuilder = Par01TopNodeBuilder(
        nodeFactory = object : Par01TopNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> {
            return TestFlowNode(initialTarget = Target.par01Top.par01TopIntro)
          }

          override fun createPar01TopIntroNode(): ScreenNode {
            return TestScreenNode()
          }
        },
        schema = Parallel01TopSchema()
      )
      val bottomNodeBuilder = Par01BottomNodeBuilder(
        nodeFactory = object : Par01BottomNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> {
            return TestFlowNode(initialTarget = Target.par01Bottom.par01BottomMain)
          }

          override fun createPar01BottomMainNode(): ScreenNode {
            return TestScreenNode()
          }
        },
        schema = Parallel01BottomSchema()
      )
      val mainNodeBuilder = Par01MainNodeBuilder(
        nodeFactory = object : Par01MainNodeBuilder.Factory {
          override fun createRootNode(): ParallelNode {
            return TestParallelNode()
          }

          override fun createPar01BottomNodeBuilder(): NodeBuilder {
            return bottomNodeBuilder
          }

          override fun createPar01TopNodeBuilder(): NodeBuilder {
            return topNodeBuilder
          }
        },
        Parallel01MainSchema(Parallel01TopSchema(), Parallel01BottomSchema())
      )
      val appNodeBuilder = Par01AppNodeBuilder(
        nodeFactory = object : Par01AppNodeBuilder.Factory {
          override fun createRootNode(): FlowNode<*> {
            return TestFlowNode(
              initialTarget = Target.par01App.par01Main
            )
          }

          override fun createPar01MainNodeBuilder(): NodeBuilder {
            return mainNodeBuilder
          }
        },
        schema = appSchema
      )
      val sut = NavigationService<Unit>(
        nodeBuilder = appNodeBuilder,
        onFinishRequest = { Ignore }
      )

      sut.collectTransitions().test {
        awaitItem().apply {
          regions.keys.map { it.path.toString() }.shouldContainOnly(
            "par01App",
            "par01App.par01Main.par01Top",
            "par01App.par01Main.par01Bottom"
          )
          regions.entries.find { it.key.path.lastSegment().name == "par01App" }
            ?.value?.alive.orEmpty().map { it.toString() }
            .shouldContainInOrder(
              "par01App",
              "par01App.par01Main",
              "par01App.par01Main.par01Top",
              "par01App.par01Main.par01Top.par01TopIntro",
              "par01App.par01Main.par01Bottom",
              "par01App.par01Main.par01Bottom.par01BottomMain"
            )
          regions.entries.find { it.key.path.lastSegment().name == "par01Top" }
            ?.value?.alive.orEmpty().map { it.toString() }
            .shouldContainInOrder(
              "par01App.par01Main.par01Top",
              "par01App.par01Main.par01Top.par01TopIntro",
            )
          regions.entries.find { it.key.path.lastSegment().name == "par01Bottom" }
            ?.value?.alive.orEmpty().map { it.toString() }
            .shouldContainInOrder(
              "par01App.par01Main.par01Bottom",
              "par01App.par01Main.par01Bottom.par01BottomMain"
            )
          // TODO test par01App.active contain both TopIntro + BottomMain
          // TODO test par01Top.active contain both TopIntro
          // TODO test par01Bottom.active contain both BottomMain
        }
      }
    }
  }
}
