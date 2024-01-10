package ru.kode.way.sample

import ru.kode.way.NavigationService
import ru.kode.way.Stay
import ru.kode.way.sample.app.routing.di.AppFlowComponentImpl

fun main() {
  val component = AppFlowComponentImpl()

  val service = NavigationService<Unit>(
    component.nodeBuilder(),
    onFinishRequest = {
      Stay
    }
  )
  service.addTransitionListener {
    println("State changed to $it")
  }
  service.start()

  while (true) {
  }
}
