package ru.kode.way.sample

import ru.kode.way.NavigationService
import ru.kode.way.Path
import ru.kode.way.RegionId
import ru.kode.way.Schema
import ru.kode.way.sample.app.routing.di.AppFlowComponentImpl

fun main() {
  val component = AppFlowComponentImpl()

  val service = NavigationService(
    object : Schema {
      override val regions: List<RegionId> = listOf(RegionId(Path("app")))
    },
    component.nodeBuilder(),
  )
  service.addTransitionListener {
    println("State changed to $it")
  }
  service.start()

  while (true) {
  }
}
