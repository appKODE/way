package ru.kode.way.fake.mc01

import ru.kode.way.FlowNode
import ru.kode.way.NodeBuilder
import ru.kode.way.Target
import ru.kode.way.TestFlowNode
import ru.kode.way.TestFlowTransitionSpec
import ru.kode.way.mc01.AppFlowNodeBuilder
import ru.kode.way.mc01.LoginFlowNodeBuilder
import ru.kode.way.mc01.MCLoginFlowSchema
import ru.kode.way.mc01.MCMainFlowSchema
import ru.kode.way.mc01.MainFlowNodeBuilder

class AppFlowNodeFactory(
  private val initialTarget: Target,
  private val flowTransitions: List<TestFlowTransitionSpec> = emptyList(),
  private val mainFlowTransitions: List<TestFlowTransitionSpec> = emptyList(),
  private val appLoginFlowTransitions: List<TestFlowTransitionSpec> = emptyList(),
  private val mainLoginFlowTransitions: List<TestFlowTransitionSpec> = emptyList()
) : AppFlowNodeBuilder.Factory {
  override fun createRootNode(): FlowNode<*> {
    return TestFlowNode(
      initialTarget,
      transitions = flowTransitions
    )
  }

  override fun createMainFlowNodeBuilder(): NodeBuilder {
    return MainFlowNodeBuilder(
      MainFlowNodeFactory(
        flowTransitions = mainFlowTransitions,
        loginFlowTransitions = mainLoginFlowTransitions,
      ),
      MCMainFlowSchema(MCLoginFlowSchema())
    )
  }

  override fun createLoginFlowNodeBuilder(section: Int): NodeBuilder {
    return LoginFlowNodeBuilder(LoginFlowNodeFactory(appLoginFlowTransitions), MCLoginFlowSchema())
  }
}
