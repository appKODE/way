package ru.kode.test.app.schema

import kotlin.Any
import kotlin.String
import kotlin.Suppress
import kotlin.collections.Map
import ru.kode.way.FlowNode
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.Path
import ru.kode.way.ScreenNode
import ru.kode.way.Segment

public class Nb01loginNodeBuilder(
  private val nodeFactory: Factory,
  private val schema: NodeBuildersTest01Schema,
) : NodeBuilder {
  public override fun build(path: Path, payloads: Map<Path, Any>): Node {
    check(path.segments.firstOrNull()?.name == "nb01login") {
      """illegal path build requested for "nb01login" node: $path"""
    }
    return when {
      path == targetOrError("nb01credentials") -> nodeFactory.createNb01credentialsNode()
      else -> error("""illegal path build requested for "nb01login" node: $path""")
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

    public fun createNb01credentialsNode(): ScreenNode
  }
}
