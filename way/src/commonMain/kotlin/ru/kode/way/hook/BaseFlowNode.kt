package ru.kode.way.hook

import ru.kode.way.Event
import ru.kode.way.FlowNode
import ru.kode.way.FlowTransition
import ru.kode.way.Ignore
import ru.kode.way.NavigationService

/**
 * A basic flow node with hooks support.
 * Create your own custom node if this one is too basic or if you don't need to use hooks.
 *
 * Requires [NodeHooksSupportExtensionPoint] to be added to [NavigationService] to work.
 */
abstract class BaseFlowNode<R : Any> : FlowNode<R>, HasFlowNodeHooks<R> {
  private val _hooks = mutableListOf<FlowNodeHook<R>>()
  override val hooks: List<FlowNodeHook<R>> = _hooks

  override fun transition(event: Event): FlowTransition<R> {
    return Ignore
  }

  override fun addHook(hook: FlowNodeHook<R>) {
    _hooks.add(hook)
  }

  override fun removeHook(hook: FlowNodeHook<R>) {
    _hooks.remove(hook)
  }
}
