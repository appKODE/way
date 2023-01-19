package ru.kode.way.sample.compose.app.routing.di

import ru.kode.way.NodeBuilder
import ru.kode.way.sample.compose.app.routing.AppFlowNode
import ru.kode.way.sample.compose.app.routing.AppFlowNodeBuilder
import ru.kode.way.sample.compose.permissions.routing.di.PermissionsFlowComponent
import ru.kode.way.sample.compose.permissions.routing.di.PermissionsFlowComponentImpl

class AppFlowComponentImpl : AppFlowComponent {
  override fun nodeBuilder(): NodeBuilder {
    return AppFlowNodeBuilder(
      flowNode = { AppFlowNode() },
      permissionsNodeBuilder = { permissionsFlowComponent().nodeBuilder() }
    )
  }

  override fun permissionsFlowComponent(): PermissionsFlowComponent {
    return PermissionsFlowComponentImpl()
  }
}
