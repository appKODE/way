package ru.kode.way.sample.compose.main.parallel.routing.head

import ru.kode.way.sample.compose.main.parallel.routing.di.MainParallelFlowComponent

object HeadFlow {
  val schema: HeadFlowSchema = HeadFlowSchema()

  fun nodeBuilder(component: MainParallelFlowComponent): HeadNodeBuilder {
    return HeadNodeBuilder(component.headNodeFactory(), schema)
  }
}
