package ru.kode.test.app.schema

import kotlin.Any
import kotlin.String
import kotlin.Suppress
import kotlin.collections.Map
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.ParallelNode
import ru.kode.way.Path
import ru.kode.way.Segment
import ru.kode.way.drop
import ru.kode.way.startsWith

public class Nbp02mainNodeBuilder(
  private val nodeFactory: Factory,
  private val schema: NodeBuildersParallel02Schema,
) : NodeBuilder {
  private val _nbp02sheetNodeBuilder: NodeBuilder by lazy(LazyThreadSafetyMode.NONE) {
      nodeFactory.createNbp02sheetNodeBuilder() }

  private val _nbp02headNodeBuilder: NodeBuilder by lazy(LazyThreadSafetyMode.NONE) {
      nodeFactory.createNbp02headNodeBuilder() }

  public override fun build(path: Path, payloads: Map<Path, Any>): Node {
    check(path.segments.firstOrNull()?.name == "nbp02main") {
      """illegal path build requested for "nbp02main" node: $path"""
    }
    return when {
      path == targetOrError("nbp02main") -> nodeFactory.createRootNode()
      path.startsWith(targetOrError("nbp02sheet")) -> {
        val targetPath = targetOrError("nbp02sheet")
        _nbp02sheetNodeBuilder.build(path.drop(targetPath.length - 1),
            payloads = payloads.mapKeys { it.key.drop(targetPath.length - 1) })
      }
      path.startsWith(targetOrError("nbp02head")) -> {
        val targetPath = targetOrError("nbp02head")
        _nbp02headNodeBuilder.build(path.drop(targetPath.length - 1),
            payloads = payloads.mapKeys { it.key.drop(targetPath.length - 1) })
      }
      else -> error("""illegal path build requested for "nbp02main" node: $path""")
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
    public fun createRootNode(): ParallelNode

    public fun createNbp02sheetNodeBuilder(): NodeBuilder

    public fun createNbp02headNodeBuilder(): NodeBuilder
  }
}
