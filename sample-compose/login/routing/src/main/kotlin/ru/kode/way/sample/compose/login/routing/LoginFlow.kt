package ru.kode.way.sample.compose.login.routing

import ru.kode.way.sample.compose.login.routing.di.LoginFlowComponent
import ru.kode.way.sample.compose.permissions.routing.PermissionsFlow

object LoginFlow {
  fun nodeBuilder(component: LoginFlowComponent): LoginNodeBuilder = LoginNodeBuilder(component.nodeFactory(), schema)

  val schema: LoginSchema = LoginSchema(PermissionsFlow.schema)
}
