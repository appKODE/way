package ru.kode.way.sample.app.routing

import ru.kode.way.FlowNode
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.Path
import ru.kode.way.ScreenNode

class AppFlowNodeBuilder(
  private val flowNode: () -> FlowNode<*, *>,
  private val permissionsNodeBuilder: () -> NodeBuilder,
): NodeBuilder {
  override fun build(path: Path): List<Node> {
    val nodes = ArrayList<Node>()
    nodes.add(flowNode())
    path.segments.forEach { segment ->
      when (segment.name) {
        // TODO cache permissionsNodeBuilder and mainNodeBuilder (lazy create them)
        "permissions" -> nodes.addAll(permissionsNodeBuilder().build(path))
//        "main" -> nodes.addAll(mainNodeBuilder().build(path))
      }
    }
    return nodes
  }
}
