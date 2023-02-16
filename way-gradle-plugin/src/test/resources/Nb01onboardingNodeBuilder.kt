package ru.kode.test.app.schema

import ru.kode.way.FlowNode
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.Path
import ru.kode.way.ScreenNode

public class Nb01onboardingNodeBuilder(
  private val nodeFactory: Factory,
) : NodeBuilder {
  private val targets: Nb01onboardingTargets = Nb01onboardingTargets(Path("nb01onboarding"))

  public override fun build(path: Path): Node {
    check(path.segments.firstOrNull()?.name == "nb01onboarding") {
      """illegal path build requested for "nb01onboarding" node: $path"""
    }
    return if (path.segments.size == 1 && path.segments.first().name == "nb01onboarding") {
      nodeFactory.createFlowNode()
    }
    else {
      when {
        path == targets.nb01intro.path -> nodeFactory.createNb01introNode()
        else -> error("""illegal path build requested for "nb01onboarding" node: $path""")
      }
    }
  }

  public interface Factory {
    public fun createFlowNode(): FlowNode<*>

    public fun createNb01introNode(): ScreenNode
  }
}
