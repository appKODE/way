package ru.kode.way

interface NodeBuilder {
  fun build(path: Path): List<Node>
}
