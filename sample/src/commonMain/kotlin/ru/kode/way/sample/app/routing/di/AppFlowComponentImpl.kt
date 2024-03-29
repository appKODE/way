package ru.kode.way.sample.app.routing.di

import ru.kode.way.FlowNode
import ru.kode.way.NodeBuilder
import ru.kode.way.sample.AppNodeBuilder
import ru.kode.way.sample.SampleAppSchema
import ru.kode.way.sample.app.routing.AppFlowNode
import ru.kode.way.sample.permissions.routing.di.PermissionsFlowComponent
import ru.kode.way.sample.permissions.routing.di.PermissionsFlowComponentImpl

class AppFlowComponentImpl : AppFlowComponent {
  override fun nodeBuilder(): NodeBuilder {
    return AppNodeBuilder(
      nodeFactory = object : AppNodeBuilder.Factory {
        override fun createRootNode(): FlowNode<*> {
          return AppFlowNode()
        }

        override fun createMainNodeBuilder(): NodeBuilder {
          TODO("not implemented")
        }

        override fun createPermissionsNodeBuilder(): NodeBuilder {
          return permissionsFlowComponent().nodeBuilder()
        }
      },
      schema = SampleAppSchema()
    )
  }

  override fun permissionsFlowComponent(): PermissionsFlowComponent {
    return PermissionsFlowComponentImpl()
  }
}
