package ru.kode.way.sample.permissions.routing.di

import ru.kode.way.NodeBuilder
import ru.kode.way.sample.permissions.routing.PermissionsFlowNode
import ru.kode.way.sample.permissions.routing.PermissionsNodeBuilder

class PermissionsFlowComponentImpl : PermissionsFlowComponent {
  override fun nodeBuilder(): NodeBuilder {
    return PermissionsNodeBuilder(
      flowNode = { PermissionsFlowNode() },
      introScreenNode = { TODO() },
      requestScreenNode = { TODO() }
    )
  }
}
