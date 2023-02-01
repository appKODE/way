package ru.kode.test.app.schema

import ru.kode.way.FlowNode
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.Path
import ru.kode.way.ScreenNode

public class Nb01loginNodeBuilder(
  private val flowNode: () -> FlowNode<*, *>,
  private val nb01credentialsNode: () -> ScreenNode<*>,
) : NodeBuilder {
  private val targets: Nb01loginTargets = Nb01loginTargets(Path("nb01login"))

  public override fun build(path: Path): Node {
    check(path.segments.firstOrNull()?.name == "nb01login") {
      """illegal path build requested for "nb01login" node: $path"""
    }
    return if (path.segments.size == 1 && path.segments.first().name == "nb01login") {
      flowNode()
    }
    else {
      when {
        path == targets.nb01credentials.path -> nb01credentialsNode()
        else -> error("""illegal path build requested for "nb01login" node: $path""")
      }
    }
  }
}
