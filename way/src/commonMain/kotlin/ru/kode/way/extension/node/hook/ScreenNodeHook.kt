package ru.kode.way.extension.node.hook

interface ScreenNodeHook {
  fun onPreEntry()
  fun onPostEntry()
  fun onPreExit()
  fun onPostExit()
}
