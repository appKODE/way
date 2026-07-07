package ru.kode.way.sample.compose.main.parallel.routing

import ru.kode.way.sample.compose.category.routing.CategoriesFlow
import ru.kode.way.sample.compose.main.parallel.routing.di.MainParallelFlowComponent
import ru.kode.way.sample.compose.main.parallel.routing.head.HeadFlow

object MainParallelFlow {
  fun nodeBuilder(component: MainParallelFlowComponent): MainParallelNodeBuilder =
    MainParallelNodeBuilder(component.nodeFactory(), schema)

  val schema: MainParallelSchema = MainParallelSchema(HeadFlow.schema, CategoriesFlow.schema)
}
