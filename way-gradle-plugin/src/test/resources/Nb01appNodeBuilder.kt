package ru.kode.test.app.schema

import ru.kode.way.FlowNode
import ru.kode.way.Ignore
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.Path
import ru.kode.way.drop
import ru.kode.way.startsWith

public class Nb01appNodeBuilder(
  private val flowNode: () -> FlowNode<*, *>,
  private val nb01loginNodeBuilder: () -> NodeBuilder,
  private val nb01onboardingNodeBuilder: () -> NodeBuilder,
) : NodeBuilder {
  private val _nb01loginNodeBuilder: NodeBuilder by lazy(LazyThreadSafetyMode.NONE) {
      nb01loginNodeBuilder() }

  private val _nb01onboardingNodeBuilder: NodeBuilder by lazy(LazyThreadSafetyMode.NONE) {
      nb01onboardingNodeBuilder() }

  private val targets: Nb01appTargets = Nb01appTargets(Path("nb01app"))

  public override fun build(path: Path): Node {
    check(path.segments.firstOrNull()?.name == "nb01app") {
      """illegal path build requested for "nb01app" node: $path"""
    }
    return if (path.segments.size == 1 && path.segments.first().name == "nb01app") {
      flowNode()
    }
    else {
      when {
        path.startsWith(targets.nb01login { Ignore }.path) ->
            _nb01loginNodeBuilder.build(path.drop(1))
        path.startsWith(targets.nb01onboarding { Ignore }.path) ->
            _nb01onboardingNodeBuilder.build(path.drop(1))
        else -> error("""illegal path build requested for "nb01app" node: $path""")
      }
    }
  }
}
