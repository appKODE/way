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

public class Nbp01mainNodeBuilder(
  private val nodeFactory: Factory,
  private val schema: NodeBuildersParallel01Schema,
) : NodeBuilder {
  private val _nbp01sheetNodeBuilder: NodeBuilder by lazy(LazyThreadSafetyMode.NONE) {
      nodeFactory.createNbp01sheetNodeBuilder() }

  private val _nbp01headNodeBuilder: NodeBuilder by lazy(LazyThreadSafetyMode.NONE) {
      nodeFactory.createNbp01headNodeBuilder() }

  public override fun build(path: Path, payloads: Map<Path, Any>): Node {
    check(path.segments.firstOrNull()?.name == "nbp01main") {
      """illegal path build requested for "nbp01main" node: $path"""
    }
    return when {
      path == targetOrError("nbp01main") -> nodeFactory.createRootNode()
      path.startsWith(targetOrError("nbp01sheet")) -> {
        val targetPath = targetOrError("nbp01sheet")
        _nbp01sheetNodeBuilder.build(path.drop(targetPath.length - 1),
            payloads = payloads.mapKeys { it.key.drop(targetPath.length - 1) })
      }
      path.startsWith(targetOrError("nbp01head")) -> {
        val targetPath = targetOrError("nbp01head")
        _nbp01headNodeBuilder.build(path.drop(targetPath.length - 1),
            payloads = payloads.mapKeys { it.key.drop(targetPath.length - 1) })
      }
      else -> error("""illegal path build requested for "nbp01main" node: $path""")
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

    public fun createNbp01sheetNodeBuilder(): NodeBuilder

    public fun createNbp01headNodeBuilder(): NodeBuilder
  }
}
