package ru.kode.way.sample.compose.main.parallel.routing.head.di

import ru.kode.way.FlowNode
import ru.kode.way.ScreenNode
import ru.kode.way.sample.compose.main.parallel.routing.head.HeadNodeBuilder
import javax.inject.Inject

class HeadFlowNodeFactory @Inject constructor() : HeadNodeBuilder.Factory {
  override fun createRootNode(): FlowNode<*> {
    TODO("not implemented")
  }

  override fun createIntroNode(): ScreenNode {
    TODO("not implemented")
  }

  override fun createHeadMainNode(): ScreenNode {
    TODO("not implemented")
  }
}
