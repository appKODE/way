package ru.kode.way

class TestNodeBuilder(
  override val schema: Schema,
  private val mapping: Map<String, Node>,
  private val throwAtBuild: Path? = null,
  private val throwAtBuildMessage: String = "TestNodeBuilder.build injected throw",
  private val throwOnInvalidateCache: Boolean = false,
  private val throwOnInvalidateCacheMessage: String = "TestNodeBuilder.invalidateCache injected throw",
) : NodeBuilder {
  override fun build(path: Path, payloads: Map<Path, Any>, rootSegmentAlias: Segment?): Node {
    if (path == throwAtBuild) error(throwAtBuildMessage)
    return mapping[path.segments.joinToString(".") { it.name }]
      ?: error("no test node mapping for path $path. Existing keys: ${mapping.keys}")
  }

  override fun invalidateCache(alivePaths: Set<Path>) {
    if (throwOnInvalidateCache) error(throwOnInvalidateCacheMessage)
  }
}
