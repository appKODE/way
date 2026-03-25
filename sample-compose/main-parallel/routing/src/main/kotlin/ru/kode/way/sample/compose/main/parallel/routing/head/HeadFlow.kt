package ru.kode.way.sample.compose.main.parallel.routing.head

import ru.kode.way.sample.compose.main.parallel.routing.di.MainParallelFlowComponent

object HeadFlow {
  val schema: HeadFlowSchema = HeadFlowSchema()

  fun nodeBuilder(component: MainParallelFlowComponent): HeadNodeBuilder =
    HeadNodeBuilder(component.headNodeFactory(), schema)
}
