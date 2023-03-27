package ru.kode.way.sample.compose.main.parallel.routing

import ru.kode.way.sample.compose.category.routing.CategoriesFlow
import ru.kode.way.sample.compose.main.parallel.routing.di.MainParallelFlowComponent

object MainParallelFlow {
  fun nodeBuilder(component: MainParallelFlowComponent): MainParallelNodeBuilder {
    return MainParallelNodeBuilder(component.nodeFactory(), schema)
  }

  val schema: MainParallelSchema = MainParallelSchema(CategoriesFlow.schema)
}
