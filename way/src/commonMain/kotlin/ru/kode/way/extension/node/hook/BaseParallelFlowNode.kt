package ru.kode.way.extension.node.hook

import ru.kode.way.ParallelFlowNode

/**
 * A parallel flow node with hooks support, the counterpart of [BaseFlowNode].
 *
 * Requires [NodeHooksSupportExtensionPoint] to be added to [ru.kode.way.NavigationService] to work.
 */
abstract class BaseParallelFlowNode<R : Any> :
  ParallelFlowNode<R>(),
  HasFlowNodeHooks<R> {
  private val _hooks = mutableListOf<FlowNodeHook<R>>()
  override val hooks: List<FlowNodeHook<R>> = _hooks

  override fun addHook(hook: FlowNodeHook<R>) {
    _hooks.add(hook)
  }

  override fun removeHook(hook: FlowNodeHook<R>) {
    _hooks.remove(hook)
  }
}
