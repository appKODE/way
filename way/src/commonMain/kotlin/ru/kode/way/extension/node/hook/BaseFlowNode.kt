package ru.kode.way.extension.node.hook

import ru.kode.way.Event
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.Ignore
import ru.kode.way.NavigationService
import ru.kode.way.Path

/**
 * A basic flow node with hooks support.
 * Create your own custom node if this one is too basic or if you don't need to use hooks.
 *
 * Requires [NodeHooksSupportExtensionPoint] to be added to [NavigationService] to work.
 *
 * Subclasses may read [nodePath] inside [transition] / [onExit] / Compose `Content` to learn
 * the absolute path this node was mounted at. It is set just before the first [onEntry] and
 * remains valid for the lifetime of the node. Reading it before entry throws — use only after
 * the runtime has activated the node.
 */
abstract class BaseFlowNode<R : Any> :
  FlowNode<R>,
  HasFlowNodeHooks<R> {
  private val _hooks = mutableListOf<FlowNodeHook<R>>()
  override val hooks: List<FlowNodeHook<R>> = _hooks

  private var _nodePath: Path? = null
  val nodePath: Path
    get() = checkNotNull(_nodePath) {
      "nodePath is not available before the runtime calls onEntry on this node"
    }

  override fun onEntry(event: Event, path: Path) {
    _nodePath = path
    onEntry(event)
  }

  override fun transition(event: Event): FlowTransition<R> = Ignore

  override fun addHook(hook: FlowNodeHook<R>) {
    _hooks.add(hook)
  }

  override fun removeHook(hook: FlowNodeHook<R>) {
    _hooks.remove(hook)
  }
}
