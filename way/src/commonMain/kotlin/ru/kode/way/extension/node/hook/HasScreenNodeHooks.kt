package ru.kode.way.extension.node.hook

interface HasScreenNodeHooks {
  val hooks: List<ScreenNodeHook>

  fun addHook(hook: ScreenNodeHook)
  fun removeHook(hook: ScreenNodeHook)
}
