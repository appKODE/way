package ru.kode.way.fake.mc01

import ru.kode.way.FlowNode
import ru.kode.way.NodeBuilder
import ru.kode.way.ScreenNode
import ru.kode.way.Target
import ru.kode.way.TestFlowNode
import ru.kode.way.TestFlowTransitionSpec
import ru.kode.way.TestScreenNode
import ru.kode.way.mc01.LoginFlowNodeBuilder
import ru.kode.way.mc01.MCLoginFlowSchema
import ru.kode.way.mc01.MainFlowNodeBuilder
import ru.kode.way.mc01.mainFlow

class MainFlowNodeFactory(
  private val flowTransitions: List<TestFlowTransitionSpec> = emptyList(),
  private val loginFlowTransitions: List<TestFlowTransitionSpec> = emptyList()
) : MainFlowNodeBuilder.Factory {
  override fun createRootNode(): FlowNode<*> {
    return TestFlowNode(Target.mainFlow.main(42), transitions = flowTransitions)
  }

  override fun createMainNode(count: Int): ScreenNode {
    return TestScreenNode()
  }

  override fun createLoginFlowNodeBuilder(section: Int): NodeBuilder {
    return LoginFlowNodeBuilder(LoginFlowNodeFactory(loginFlowTransitions), MCLoginFlowSchema())
  }
}
