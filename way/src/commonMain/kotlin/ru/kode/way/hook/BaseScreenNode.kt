package ru.kode.way.hook

import ru.kode.way.ScreenNode

/**
 * A basic screen node with hooks support.
 * Create your own custom node if this one is too basic or if you don't need to use hooks.
 *
 * Requires [NodeHooksSupportExtensionPoint] to be added to [NavigationService] to work.
 */
abstract class BaseScreenNode<R : Any> : ScreenNode, HasScreenNodeHooks {
  private val _hooks = mutableListOf<ScreenNodeHook>()
  override val hooks: List<ScreenNodeHook> = _hooks

  override fun addHook(hook: ScreenNodeHook) {
    _hooks.add(hook)
  }

  override fun removeHook(hook: ScreenNodeHook) {
    _hooks.remove(hook)
  }
}
