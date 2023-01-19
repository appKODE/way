package ru.kode.way.sample

import ru.kode.way.NavigationService
import ru.kode.way.SampleAppSchema
import ru.kode.way.sample.app.routing.di.AppFlowComponentImpl

fun main() {
  val component = AppFlowComponentImpl()

  val service = NavigationService(
    SampleAppSchema(),
    component.nodeBuilder(),
  )
  service.addTransitionListener {
    println("State changed to $it")
  }
  service.start()

  while (true) {
  }
}
