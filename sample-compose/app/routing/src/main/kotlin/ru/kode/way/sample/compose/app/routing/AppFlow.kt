package ru.kode.way.sample.compose.app.routing

import ru.kode.way.sample.compose.app.routing.di.AppFlowComponent
import ru.kode.way.sample.compose.login.routing.LoginFlow
import ru.kode.way.sample.compose.main.parallel.routing.MainParallelFlow
import ru.kode.way.sample.compose.main.routing.MainFlow

object AppFlow {
  fun nodeBuilder(component: AppFlowComponent): AppNodeBuilder {
    return AppNodeBuilder(component.nodeFactory(), schema)
  }

  val schema: AppSchema = AppSchema(
    LoginFlow.schema, MainFlow.schema, MainParallelFlow.schema
  )
}
