package ru.kode.way.sample.compose.permissions.routing

import ru.kode.way.sample.compose.permissions.routing.di.PermissionsFlowComponent

object PermissionsFlow {
  fun nodeBuilder(component: PermissionsFlowComponent): PermissionsNodeBuilder {
    return PermissionsNodeBuilder(component.nodeFactory(), schema)
  }

  val schema: PermissionsSchema = PermissionsSchema()
}
