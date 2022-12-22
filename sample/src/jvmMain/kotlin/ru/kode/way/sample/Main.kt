package ru.kode.way.sample

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import ru.kode.way.NavigationService
import ru.kode.way.Schema
import ru.kode.way.sample.app.routing.di.AppFlowComponentImpl

fun main() {
  val component = AppFlowComponentImpl()

  val service = NavigationService(
    object : Schema {},
    component.nodeBuilder(),
  )
  service.start()

  runBlocking {
    service.states
      .onEach {
        println("nav state changed to $it")
      }
      .launchIn(this)
  }
}
