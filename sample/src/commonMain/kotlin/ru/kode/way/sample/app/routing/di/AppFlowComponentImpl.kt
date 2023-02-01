package ru.kode.way.sample.app.routing.di

import ru.kode.way.NodeBuilder
import ru.kode.way.sample.AppNodeBuilder
import ru.kode.way.sample.app.routing.AppFlowNode
import ru.kode.way.sample.permissions.routing.di.PermissionsFlowComponent
import ru.kode.way.sample.permissions.routing.di.PermissionsFlowComponentImpl

class AppFlowComponentImpl : AppFlowComponent {
  override fun nodeBuilder(): NodeBuilder {
    return AppNodeBuilder(
      flowNode = { AppFlowNode() },
      permissionsNodeBuilder = { permissionsFlowComponent().nodeBuilder() },
      mainNodeBuilder = { TODO() }
    )
  }

  override fun permissionsFlowComponent(): PermissionsFlowComponent {
    return PermissionsFlowComponentImpl()
  }
}
