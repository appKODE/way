package ru.kode.way.sample

import ru.kode.way.NavigationService
import ru.kode.way.Path
import ru.kode.way.RegionId
import ru.kode.way.Schema
import ru.kode.way.Segment
import ru.kode.way.sample.app.routing.di.AppFlowComponentImpl

fun main() {
  val component = AppFlowComponentImpl()

  val service = NavigationService(
    object : Schema {
      override val regions: List<RegionId> = listOf(RegionId(Path("app")))
      override fun children(regionId: RegionId): Set<Segment> {
        TODO("not implemented")
      }

      override fun children(regionId: RegionId, segment: Segment): Set<Segment> {
        TODO("not implemented")
      }

      override fun targets(regionId: RegionId): Map<Segment, Path> {
        TODO("not implemented")
      }

      override fun nodeType(regionId: RegionId, path: Path): Schema.NodeType {
        TODO("not implemented")
      }
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
