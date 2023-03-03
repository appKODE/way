package ru.kode.way

interface NodeExtensionPoint {
  fun onPreEntry(node: Node, path: Path)
  fun onPostEntry(node: Node, path: Path)
  fun onPreExit(node: Node, path: Path)
  fun onPostExit(node: Node, path: Path)

  fun onPreTransition(node: Node, path: Path, event: Event)
  fun onPostTransition(node: Node, path: Path, event: Event, transition: Transition)
}
