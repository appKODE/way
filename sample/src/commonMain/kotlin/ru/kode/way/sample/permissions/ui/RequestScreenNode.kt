package ru.kode.way.sample.permissions.ui

import ru.kode.way.Event
import ru.kode.way.ScreenNode
import ru.kode.way.ScreenTransition
import ru.kode.way.Stay

class RequestScreenNode : ScreenNode {
  override fun transition(event: Event): ScreenTransition {
    return Stay
  }

  fun Content() {
  }
}
