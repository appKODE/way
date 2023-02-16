package ru.kode.test.app.schema

import ru.kode.way.FlowNode
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.Path
import ru.kode.way.ScreenNode

public class Nb02appNodeBuilder(
  private val nodeFactory: Factory,
) : NodeBuilder {
  private val targets: Nb02appTargets = Nb02appTargets(Path("nb02app"))

  public override fun build(path: Path): Node {
    check(path.segments.firstOrNull()?.name == "nb02app") {
      """illegal path build requested for "nb02app" node: $path"""
    }
    return if (path.segments.size == 1 && path.segments.first().name == "nb02app") {
      nodeFactory.createFlowNode()
    }
    else {
      when {
        path == targets.nb02screen3.path -> nodeFactory.createNb02screen3Node()
        path == targets.nb02screen1.path -> nodeFactory.createNb02screen1Node()
        path == targets.nb02screen2.path -> nodeFactory.createNb02screen2Node()
        else -> error("""illegal path build requested for "nb02app" node: $path""")
      }
    }
  }

  public interface Factory {
    public fun createFlowNode(): FlowNode<*>

    public fun createNb02screen3Node(): ScreenNode

    public fun createNb02screen1Node(): ScreenNode

    public fun createNb02screen2Node(): ScreenNode
  }
}
