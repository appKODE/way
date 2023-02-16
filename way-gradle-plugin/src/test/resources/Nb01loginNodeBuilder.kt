package ru.kode.test.app.schema

import ru.kode.way.FlowNode
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.Path
import ru.kode.way.ScreenNode

public class Nb01loginNodeBuilder(
  private val nodeFactory: Factory,
) : NodeBuilder {
  private val targets: Nb01loginTargets = Nb01loginTargets(Path("nb01login"))

  public override fun build(path: Path): Node {
    check(path.segments.firstOrNull()?.name == "nb01login") {
      """illegal path build requested for "nb01login" node: $path"""
    }
    return if (path.segments.size == 1 && path.segments.first().name == "nb01login") {
      nodeFactory.createFlowNode()
    }
    else {
      when {
        path == targets.nb01credentials.path -> nodeFactory.createNb01credentialsNode()
        else -> error("""illegal path build requested for "nb01login" node: $path""")
      }
    }
  }

  public interface Factory {
    public fun createFlowNode(): FlowNode<*>

    public fun createNb01credentialsNode(): ScreenNode
  }
}
