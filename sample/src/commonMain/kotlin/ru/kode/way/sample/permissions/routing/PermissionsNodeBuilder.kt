package ru.kode.way.sample.permissions.routing

import ru.kode.way.FlowNode
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.Path
import ru.kode.way.ScreenNode

class PermissionsNodeBuilder(
  private val flowNode: () -> FlowNode<*, *>,
  private val introScreenNode: () -> ScreenNode,
  private val requestScreenNode: () -> ScreenNode,
) : NodeBuilder {
  override fun build(path: Path): Node {
    check(path.segments.firstOrNull()?.name == "permissions") {
      "illegal path build requested for \"permissions\" node: $path"
    }
    println("[PermissionsFlowNodeBuilder] building node for path $path")
    return when {
      path.segments.size == 1 && path.segments.first().name == "permissions" -> flowNode()
      path.segments.size > 1 && path.segments[1].name == "intro" -> {
        introScreenNode()
      }
      path.segments.size > 1 && path.segments[1].name == "request" -> {
        requestScreenNode()
      }
      else -> error("illegal path build requested for \"app\" node: $path")
    }
  }
}
