package ru.kode.way.sample.compose.permissions.routing.di

import ru.kode.way.NodeBuilder
import ru.kode.way.sample.compose.permissions.routing.PermissionsNodeBuilder
import ru.kode.way.sample.compose.permissions.ui.IntroScreenNode
import ru.kode.way.sample.compose.permissions.ui.RequestScreenNode
import ru.kode.way.sample.compose.permissions.routing.PermissionsFlowNode

class PermissionsFlowComponentImpl : PermissionsFlowComponent {
  override fun nodeBuilder(): NodeBuilder {
    return PermissionsNodeBuilder(
      flowNode = { PermissionsFlowNode() },
      introScreenNode = { IntroScreenNode() },
      requestScreenNode = { RequestScreenNode() }
    )
  }
}
