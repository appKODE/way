package ru.kode.way

import kotlinx.coroutines.flow.Flow

class NavigationService(val schema: Schema, nodeBuilder: NodeBuilder) {

  fun start() {
  }

  val states: Flow<NavigationState> get() {
    TODO()
  }

  fun sendEvent(event: Event) {

  }
}
