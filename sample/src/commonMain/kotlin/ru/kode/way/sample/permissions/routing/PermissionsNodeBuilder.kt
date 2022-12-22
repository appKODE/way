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
): NodeBuilder {
  override fun build(path: Path): Map<Path, Node> {
    // TODO rework this to not create nodes if they will be discarded.
    // for example if service will call this
    // build("permissions.intro")
    // build("permissions.intro.request")
    // then each time all nodes will be created, but for the second build actually only request is needed,
    // others should already be in backstack
    val nodes = mutableMapOf<Path, Node>()
    path.segments.forEachIndexed { index, segment ->
      val subPath = Path(path.segments.take(index + 1))
      when (segment.name) {
        "permissions" -> nodes[subPath] = flowNode()
        "intro" -> nodes[subPath] = introScreenNode()
        "request" -> nodes[subPath] = requestScreenNode()
        else -> error("illegal path build requested for \"permissions\" node: $path")
      }
    }
    return nodes
  }
}
