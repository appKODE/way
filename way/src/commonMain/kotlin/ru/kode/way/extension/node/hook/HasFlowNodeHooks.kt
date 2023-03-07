package ru.kode.way.extension.node.hook

interface HasFlowNodeHooks<R : Any> {
  val hooks: List<FlowNodeHook<R>>

  fun addHook(hook: FlowNodeHook<R>)
  fun removeHook(hook: FlowNodeHook<R>)
}
