package ru.kode.way.hook

interface HasFlowNodeHooks<R : Any> {
  val hooks: List<FlowNodeHook<R>>

  fun addHook(hook: FlowNodeHook<R>)
  fun removeHook(hook: FlowNodeHook<R>)
}
