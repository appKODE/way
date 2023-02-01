package ru.kode.way.sample.permissions.routing.di

import ru.kode.way.NodeBuilder
import ru.kode.way.sample.PermissionsNodeBuilder
import ru.kode.way.sample.permissions.routing.PermissionsFlowNode
import ru.kode.way.sample.permissions.ui.IntroScreenNode
import ru.kode.way.sample.permissions.ui.RequestScreenNode

class PermissionsFlowComponentImpl : PermissionsFlowComponent {
  override fun nodeBuilder(): NodeBuilder {
    return PermissionsNodeBuilder(
      flowNode = { PermissionsFlowNode() },
      introNode = { IntroScreenNode() },
      requestNode = { RequestScreenNode() }
    )
  }
}
