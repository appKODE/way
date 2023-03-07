package ru.kode.way.extension.node.hook

import ru.kode.way.Event
import ru.kode.way.FlowTransition
import ru.kode.way.Node
import ru.kode.way.NodeExtensionPoint
import ru.kode.way.Path
import ru.kode.way.Transition

class NodeHooksSupportExtensionPoint : NodeExtensionPoint {
  override fun onPreEntry(node: Node, path: Path) {
    when (node) {
      is HasFlowNodeHooks<*> -> node.hooks.forEach { it.onPreEntry() }
      is HasScreenNodeHooks -> node.hooks.forEach { it.onPreEntry() }
      else -> Unit
    }
  }

  override fun onPostEntry(node: Node, path: Path) {
    when (node) {
      is HasFlowNodeHooks<*> -> node.hooks.forEach { it.onPostEntry() }
      is HasScreenNodeHooks -> node.hooks.forEach { it.onPostEntry() }
      else -> Unit
    }
  }

  override fun onPreExit(node: Node, path: Path) {
    when (node) {
      is HasFlowNodeHooks<*> -> node.hooks.forEach { it.onPreExit() }
      is HasScreenNodeHooks -> node.hooks.forEach { it.onPreExit() }
      else -> Unit
    }
  }

  override fun onPostExit(node: Node, path: Path) {
    when (node) {
      is HasFlowNodeHooks<*> -> node.hooks.forEach { it.onPostExit() }
      is HasScreenNodeHooks -> node.hooks.forEach { it.onPostExit() }
      else -> Unit
    }
  }

  override fun onPreTransition(node: Node, path: Path, event: Event) {
    when (node) {
      is HasFlowNodeHooks<*> -> node.hooks.forEach { it.onPreTransition(event) }
      else -> Unit
    }
  }

  override fun onPostTransition(node: Node, path: Path, event: Event, transition: Transition) {
    when (node) {
      is HasFlowNodeHooks<*> -> node.hooks.forEach {
        it.onPostTransition(event, transition as FlowTransition<Nothing>)
      }
      else -> Unit
    }
  }
}
