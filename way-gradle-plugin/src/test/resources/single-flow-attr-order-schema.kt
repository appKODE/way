package ru.kode.test.app.schema

import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.Set
import ru.kode.way.Path
import ru.kode.way.RegionId
import ru.kode.way.Schema
import ru.kode.way.Segment

public class TestAppSchema : Schema {
  public override val regions: List<RegionId> = listOf(RegionId(Path("app")))

  public override fun children(regionId: RegionId): Set<Segment> = emptySet()

  public override fun children(regionId: RegionId, segment: Segment): Set<Segment> = emptySet()

  public override fun targets(regionId: RegionId): Map<Segment, Path> = when (regionId) {
    regions[0] -> {
      mapOf(
      Segment("app") to Path("app"),
      Segment("screen1") to Path("app","screen1"),
      Segment("screen2") to Path("app","screen1","screen2"),
      Segment("screen3") to Path("app","screen1","screen2","screen3"),
      )
    }
    else -> {
      error("""unknown regionId=$regionId""")
    }
  }

  public override fun nodeType(regionId: RegionId, path: Path): Schema.NodeType = when (regionId) {
    regions[0] -> {
      when (path.segments.last().name) {
        "app" -> Schema.NodeType.Flow
        "screen1" -> Schema.NodeType.Screen
        "screen2" -> Schema.NodeType.Screen
        "screen3" -> Schema.NodeType.Screen
        else -> {
          error("""internal error: no nodeType for path=$path""")
        }
      }
    }
    else -> {
      error("""unknown regionId=$regionId""")
    }
  }
}
