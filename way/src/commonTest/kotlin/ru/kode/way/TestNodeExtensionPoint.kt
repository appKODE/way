package ru.kode.way

class TestNodeExtensionPoint(
  private val preEntry: (node: Node, path: Path) -> Unit = { _, _ -> },
  private val postEntry: (node: Node, path: Path) -> Unit = { _, _ -> },
  private val preExit: (node: Node, path: Path) -> Unit = { _, _ -> },
  private val postExit: (node: Node, path: Path) -> Unit = { _, _ -> },
  private val preDispose: (node: Node, path: Path) -> Unit = { _, _ -> },
  private val postDispose: (node: Node, path: Path) -> Unit = { _, _ -> },
  private val preTransition: (node: Node, path: Path, event: Event) -> Unit = { _, _, _ -> },
  private val postTransition: (node: Node, path: Path, event: Event, transition: Transition) -> Unit =
    { _, _, _, _ -> },
) : NodeExtensionPoint {
  override fun onPreEntry(node: Node, path: Path) {
    preEntry(node, path)
  }

  override fun onPostEntry(node: Node, path: Path) {
    postEntry(node, path)
  }

  override fun onPreExit(node: Node, path: Path) {
    preExit(node, path)
  }

  override fun onPostExit(node: Node, path: Path) {
    postExit(node, path)
  }

  override fun onPreDispose(node: Node, path: Path) {
    preDispose(node, path)
  }

  override fun onPostDispose(node: Node, path: Path) {
    postDispose(node, path)
  }

  override fun onPreTransition(node: Node, path: Path, event: Event) {
    preTransition(node, path, event)
  }

  override fun onPostTransition(node: Node, path: Path, event: Event, transition: Transition) {
    postTransition(node, path, event, transition)
  }
}
