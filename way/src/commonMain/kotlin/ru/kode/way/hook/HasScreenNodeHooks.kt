package ru.kode.way.hook

interface HasScreenNodeHooks {
  val hooks: List<ScreenNodeHook>

  fun addHook(hook: ScreenNodeHook)
  fun removeHook(hook: ScreenNodeHook)
}
