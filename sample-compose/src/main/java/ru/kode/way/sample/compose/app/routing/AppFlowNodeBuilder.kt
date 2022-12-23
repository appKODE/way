package ru.kode.way.sample.compose.app.routing

import ru.kode.way.FlowNode
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.Path
import ru.kode.way.drop

class AppFlowNodeBuilder(
  private val flowNode: () -> FlowNode<*, *>,
  private val permissionsNodeBuilder: () -> NodeBuilder,
) : NodeBuilder {
  override fun build(path: Path): Node {
    check(path.segments.firstOrNull()?.name == "app") {
      "illegal path build requested for \"app\" node: $path"
    }
    println("[AppFlowNodeBuilder] building node for path $path")

    // TODO cache permissionsNodeBuilder and mainNodeBuilder (lazy create them)
    return when {
      path.segments.size == 1 && path.segments.first().name == "app" -> flowNode()
      path.segments.size > 1 && path.segments[1].name == "permissions" -> {
        permissionsNodeBuilder().build(path.drop(1))
      }
//      path.segments.size > 1 && path.segments[1].name == "main" -> {
//        mainNodeBuilder().build(path.drop(1))
//      }
      else -> error("illegal path build requested for \"app\" node: $path")
    }
  }
}
