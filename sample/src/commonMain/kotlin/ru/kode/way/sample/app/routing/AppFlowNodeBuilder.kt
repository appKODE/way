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
  override fun build(path: Path): Map<Path, Node> {
    val nodes = mutableMapOf<Path, Node>()

    path.segments.forEachIndexed { index, segment ->
      val subPath = Path(path.segments.take(index + 1))
      when (segment.name) {
        "app" -> nodes[subPath] = flowNode()
        // TODO cache permissionsNodeBuilder and mainNodeBuilder (lazy create them)
        // TODO transform paths returned by permissionsNodeBuilder into paths relative to current flow, i.e.
        //   permissionsNodeBuilder will return paths like
        //   - permissions.intro
        //   - permissions.request
        //   they will need to be transformed to
        //   - app.permissions.intro
        //   - app.permissions.request
        //   before return
        "permissions" -> {
          nodes.putAll(permissionsNodeBuilder().build(path.drop(index)).mapKeys { (p, _) -> p.prepend(path.take(index))})
          return@forEachIndexed
        }
//        "main" -> nodes.addAll(mainNodeBuilder().build(path))
        else -> error("illegal path build requested for \"app\" node: $path")
      }
    }
    return nodes
  }
}
