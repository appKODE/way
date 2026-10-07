package ru.kode.way

interface NodeExtensionPoint {
  fun onPreEntry(node: Node, path: Path) = Unit
  fun onPostEntry(node: Node, path: Path) = Unit
  fun onPreExit(node: Node, path: Path) = Unit
  fun onPostExit(node: Node, path: Path) = Unit
  fun onPreDispose(node: Node, path: Path) = Unit
  fun onPostDispose(node: Node, path: Path) = Unit

  fun onPreTransition(node: Node, path: Path, event: Event) = Unit
  fun onPostTransition(node: Node, path: Path, event: Event, transition: Transition) = Unit
}
