package ru.kode.way.hook

import ru.kode.way.Event
import ru.kode.way.FlowTransition

interface FlowNodeHook<R : Any> {
  fun onPreEntry()
  fun onPostEntry()
  fun onPreTransition(event: Event)
  fun onPostTransition(event: Event, transition: FlowTransition<R>)
  fun onPreExit()
  fun onPostExit()
}
