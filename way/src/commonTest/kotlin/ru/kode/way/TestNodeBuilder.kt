package ru.kode.way

class TestNodeBuilder(
  private val mapping: Map<String, Node>
) : NodeBuilder {
  override fun build(path: Path): Map<Path, Node> {
    print("[TestNodeBuilder] building path $path")
    val nodes = mutableMapOf<Path, Node>()

    path.segments.forEachIndexed { index, segment ->
      val subPath = Path(path.segments.take(index + 1))
      nodes[subPath] = mapping[subPath.segments.joinToString(".") { it.name }]
        ?: error("no test node mapping for path $subPath. Existing keys: ${mapping.keys}")
    }
    println(" → ${nodes.size} nodes built")
    return nodes
  }
}
