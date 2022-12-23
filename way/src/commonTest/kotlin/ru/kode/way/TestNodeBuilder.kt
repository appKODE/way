package ru.kode.way

class TestNodeBuilder(
  private val mapping: Map<String, Node>
) : NodeBuilder {
  override fun build(path: Path): Node {
    print("[TestNodeBuilder] building path $path")

    return mapping[path.segments.joinToString(".") { it.name }]
      ?: error("no test node mapping for path $path. Existing keys: ${mapping.keys}")
  }
}
