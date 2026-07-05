package ru.kode.way.extension.node.hook

/**
 * Lifecycle hook for [ru.kode.way.ScreenNode] instances.
 *
 * **Lifecycle vs. transition callbacks:** These callbacks fire once for the node lifecycle —
 * [onPreEntry]/[onPostEntry] when the node is entered, and [onPreExit]/[onPostExit] when it is
 * exited — NOT for every individual navigation transition that happens while the node is alive.
 *
 * **Contrast with [FlowNodeHook]:** [FlowNodeHook] additionally provides [FlowNodeHook.onPreTransition]
 * and [FlowNodeHook.onPostTransition] which fire on every event processed by the flow node while
 * it is alive. `ScreenNodeHook` deliberately omits these because screen nodes are expected to be
 * replaced (not kept alive) during navigation.
 *
 * If you need to observe individual transitions while a screen node is active, use a
 * [ru.kode.way.NodeExtensionPoint] instead.
 *
 * Requires [NodeHooksSupportExtensionPoint] to be added to
 * [ru.kode.way.NavigationService] and the screen node to implement [HasScreenNodeHooks].
 */
interface ScreenNodeHook {
  fun onPreEntry()
  fun onPostEntry()
  fun onPreExit()
  fun onPostExit()
  fun onPreDispose() {}
  fun onPostDispose() {}
}
