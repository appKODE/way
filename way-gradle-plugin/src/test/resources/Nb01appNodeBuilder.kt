package ru.kode.test.app.schema

import kotlin.Any
import kotlin.String
import kotlin.Suppress
import kotlin.collections.Map
import ru.kode.way.FlowNode
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.Path
import ru.kode.way.Segment
import ru.kode.way.drop
import ru.kode.way.startsWith

public class Nb01appNodeBuilder(
  private val nodeFactory: Factory,
  private val schema: NodeBuildersTest01Schema,
) : NodeBuilder {
  private val _nb01loginNodeBuilder: NodeBuilder by lazy(LazyThreadSafetyMode.NONE) {
      nodeFactory.createNb01loginNodeBuilder() }

  private val _nb01onboardingNodeBuilder: NodeBuilder by lazy(LazyThreadSafetyMode.NONE) {
      nodeFactory.createNb01onboardingNodeBuilder() }

  public override fun build(path: Path, payloads: Map<Path, Any>): Node {
    check(path.segments.firstOrNull()?.name == "nb01app") {
      """illegal path build requested for "nb01app" node: $path"""
    }
    return when {
      path == targetOrError("nb01app") -> nodeFactory.createFlowNode()
      path.startsWith(targetOrError("nb01login")) -> {
        val targetPath = targetOrError("nb01login")
        _nb01loginNodeBuilder.build(path.drop(targetPath.length - 1),
            payloads = payloads.mapKeys { it.key.drop(targetPath.length - 1) })
      }
      path.startsWith(targetOrError("nb01onboarding")) -> {
        val targetPath = targetOrError("nb01onboarding")
        _nb01onboardingNodeBuilder.build(path.drop(targetPath.length - 1),
            payloads = payloads.mapKeys { it.key.drop(targetPath.length - 1) })
      }
      else -> error("""illegal path build requested for "nb01app" node: $path""")
    }
  }

  public fun targetOrError(segmentName: String): Path =
      schema.target(schema.regions.first(), Segment(segmentName)) ?:
      error("""internal error: no target generated for segment "$segmentName"""")

  @Suppress("UNCHECKED_CAST")
  public fun <T> payloadOrError(segmentName: String, payloads: Map<Path, Any>): T {
    val targetPath = targetOrError(segmentName)
    val payload = payloads[targetPath] ?: error("""no payload for "$targetPath"""")
    return payload as T
  }

  public interface Factory {
    public fun createFlowNode(): FlowNode<*>

    public fun createNb01loginNodeBuilder(): NodeBuilder

    public fun createNb01onboardingNodeBuilder(): NodeBuilder
  }
}
