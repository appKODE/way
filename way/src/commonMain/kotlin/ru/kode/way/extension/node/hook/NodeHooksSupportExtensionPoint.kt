package ru.kode.way.extension.node.hook

import ru.kode.way.Event
import ru.kode.way.FlowTransition
import ru.kode.way.Node
import ru.kode.way.NodeExtensionPoint
import ru.kode.way.Path
import ru.kode.way.Transition

/**
 * A [ru.kode.way.NodeExtensionPoint] that dispatches lifecycle callbacks to registered hooks.
 *
 * **Hook asymmetry:** [HasFlowNodeHooks] nodes receive both lifecycle callbacks
 * ([FlowNodeHook.onPreEntry]/[FlowNodeHook.onPostEntry]/[FlowNodeHook.onPreExit]/[FlowNodeHook.onPostExit])
 * and per-transition callbacks ([FlowNodeHook.onPreTransition]/[FlowNodeHook.onPostTransition]).
 * [HasScreenNodeHooks] nodes only receive lifecycle callbacks ([ScreenNodeHook]); they do not
 * receive per-transition callbacks. To observe individual transitions for a screen node, register
 * a [ru.kode.way.NodeExtensionPoint] directly on [ru.kode.way.NavigationService].
 */
class NodeHooksSupportExtensionPoint : NodeExtensionPoint {
  /**
   * Dispatches one lifecycle callback to whichever hook collection [node] carries: [flow] for a
   * [HasFlowNodeHooks] node, [screen] for a [HasScreenNodeHooks] node, nothing otherwise. The six
   * lifecycle overrides differ only by which hook method they invoke, so they all route through here.
   */
  private inline fun dispatch(node: Node, flow: (FlowNodeHook<*>) -> Unit, screen: (ScreenNodeHook) -> Unit) {
    when (node) {
      is HasFlowNodeHooks<*> -> node.hooks.forEach(flow)
      is HasScreenNodeHooks -> node.hooks.forEach(screen)
      else -> Unit
    }
  }

  override fun onPreEntry(node: Node, path: Path) = dispatch(node, { it.onPreEntry() }, { it.onPreEntry() })

  override fun onPostEntry(node: Node, path: Path) = dispatch(node, { it.onPostEntry() }, { it.onPostEntry() })

  override fun onPreExit(node: Node, path: Path) = dispatch(node, { it.onPreExit() }, { it.onPreExit() })

  override fun onPostExit(node: Node, path: Path) = dispatch(node, { it.onPostExit() }, { it.onPostExit() })

  override fun onPreDispose(node: Node, path: Path) = dispatch(node, { it.onPreDispose() }, { it.onPreDispose() })

  override fun onPostDispose(node: Node, path: Path) = dispatch(node, { it.onPostDispose() }, { it.onPostDispose() })

  override fun onPreTransition(node: Node, path: Path, event: Event) {
    when (node) {
      is HasFlowNodeHooks<*> -> node.hooks.forEach { it.onPreTransition(event) }
      else -> Unit
    }
  }

  override fun onPostTransition(node: Node, path: Path, event: Event, transition: Transition) {
    when (node) {
      is HasFlowNodeHooks<*> -> node.hooks.forEach {
        // Safe: HasFlowNodeHooks branch guarantees transition came from a FlowNode; JVM erases the type parameter.
        @Suppress("UNCHECKED_CAST")
        it.onPostTransition(event, transition as FlowTransition<Nothing>)
      }

      else -> Unit
    }
  }
}
