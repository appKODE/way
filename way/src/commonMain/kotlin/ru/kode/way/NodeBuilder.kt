package ru.kode.way

interface NodeBuilder {
  fun build(path: Path, payloads: Map<Path, Any>): Node
  fun invalidateCache(path: Path)
}
