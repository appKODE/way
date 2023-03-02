package ru.kode.way.sample.compose.main.routing

import ru.kode.way.sample.compose.main.routing.di.MainFlowComponent

object MainFlow {
  fun nodeBuilder(component: MainFlowComponent): MainNodeBuilder {
    return MainNodeBuilder(component.nodeFactory(), schema)
  }

  val schema: MainSchema = MainSchema()
}
