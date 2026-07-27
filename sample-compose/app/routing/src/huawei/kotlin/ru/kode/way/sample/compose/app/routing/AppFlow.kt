package ru.kode.way.sample.compose.app.routing

import ru.kode.way.sample.compose.app.routing.di.AppFlowComponent
import ru.kode.way.sample.compose.main.parallel.routing.MainParallelFlow
import ru.kode.way.sample.compose.main.routing.MainFlow

object AppFlow {
  fun nodeBuilder(component: AppFlowComponent): AppNodeBuilder = AppNodeBuilder(component.nodeFactory(), schema)

  val schema: AppSchema = AppSchema(
    MainFlow.schema,
    MainParallelFlow.schema,
  )
}
