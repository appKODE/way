package ru.kode.way.fake.mc01

import ru.kode.way.FlowNode
import ru.kode.way.ScreenNode
import ru.kode.way.Target
import ru.kode.way.TestFlowNode
import ru.kode.way.TestFlowTransitionSpec
import ru.kode.way.TestScreenNode
import ru.kode.way.mc01.LoginFlowNodeBuilder
import ru.kode.way.mc01.loginFlow

class LoginFlowNodeFactory(
  private val flowTransitions: List<TestFlowTransitionSpec> = emptyList()
) : LoginFlowNodeBuilder.Factory {
  override fun createRootNode(section: Int): FlowNode<*> {
    return TestFlowNode(Target.loginFlow.credentials, transitions = flowTransitions)
  }

  override fun createCredentialsNode(): ScreenNode {
    return TestScreenNode()
  }

  override fun createOtpNode(): ScreenNode {
    return TestScreenNode()
  }
}
