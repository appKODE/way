package ru.kode.test.app.schema

import ru.kode.way.FlowNode
import ru.kode.way.Ignore
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.Path
import ru.kode.way.drop
import ru.kode.way.startsWith

public class Nb01appNodeBuilder(
  private val nodeFactory: Factory,
) : NodeBuilder {
  private val _nb01loginNodeBuilder: NodeBuilder by lazy(LazyThreadSafetyMode.NONE) {
      nodeFactory.createNb01loginNodeBuilder() }

  private val _nb01onboardingNodeBuilder: NodeBuilder by lazy(LazyThreadSafetyMode.NONE) {
      nodeFactory.createNb01onboardingNodeBuilder() }

  private val targets: Nb01appTargets = Nb01appTargets(Path("nb01app"))

  public override fun build(path: Path): Node {
    check(path.segments.firstOrNull()?.name == "nb01app") {
      """illegal path build requested for "nb01app" node: $path"""
    }
    return if (path.segments.size == 1 && path.segments.first().name == "nb01app") {
      nodeFactory.createFlowNode()
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

  public interface Factory {
    public fun createFlowNode(): FlowNode<*>

    public fun createNb01loginNodeBuilder(): NodeBuilder

    public fun createNb01onboardingNodeBuilder(): NodeBuilder
  }
}
