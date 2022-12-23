package ru.kode.way.sample.app.routing

import ru.kode.way.FlowNode
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.Path
import ru.kode.way.ScreenNode
import ru.kode.way.drop
import ru.kode.way.head
import ru.kode.way.prepend
import ru.kode.way.tail
import ru.kode.way.take

class AppFlowNodeBuilder(
  private val flowNode: () -> FlowNode<*, *>,
  private val permissionsNodeBuilder: () -> NodeBuilder,
): NodeBuilder {
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
