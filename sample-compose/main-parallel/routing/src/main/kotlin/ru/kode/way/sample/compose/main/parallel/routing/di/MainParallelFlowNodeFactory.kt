package ru.kode.way.sample.compose.main.parallel.routing.di

import ru.kode.way.NodeBuilder
import ru.kode.way.sample.compose.category.routing.CategoriesFlow
import ru.kode.way.sample.compose.main.parallel.routing.MainParallelFlowNode
import ru.kode.way.sample.compose.main.parallel.routing.MainParallelNodeBuilder
import ru.kode.way.sample.compose.main.parallel.routing.head.HeadFlow
import javax.inject.Inject
import javax.inject.Provider

class MainParallelFlowNodeFactory @Inject constructor(
  private val flowNode: Provider<MainParallelFlowNode>,
  private val component: MainParallelFlowComponent
) : MainParallelNodeBuilder.Factory {
  override fun createRootNode(): MainParallelFlowNode = flowNode.get()
  override fun createSheetNodeBuilder(): NodeBuilder {
    return CategoriesFlow.nodeBuilder()
  }

  override fun createHeadNodeBuilder(): NodeBuilder {
    return HeadFlow.nodeBuilder(component)
  }
}
