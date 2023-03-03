package ru.kode.way.hook

interface ScreenNodeHook {
  fun onPreEntry()
  fun onPostEntry()
  fun onPreExit()
  fun onPostExit()
}
