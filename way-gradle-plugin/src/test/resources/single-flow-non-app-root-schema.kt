package ru.kode.test.app.schema

import kotlin.collections.List
import kotlin.collections.Set
import ru.kode.way.Path
import ru.kode.way.RegionId
import ru.kode.way.Schema
import ru.kode.way.Segment

public class TestAppSchema : Schema {
  public override val regions: List<RegionId> = listOf(RegionId(Path("permissions")))

  public override fun children(regionId: RegionId): Set<Segment> = emptySet()

  public override fun children(regionId: RegionId, segment: Segment): Set<Segment> = emptySet()

  public override fun target(regionId: RegionId, segment: Segment): Path? = when (regionId) {
    regions[0] -> {
      when(segment.name) {
        "permissions" -> Path("permissions")
        "screen1" -> Path("permissions", "screen1")
        "screen2" -> Path("permissions", "screen1", "screen2")
        "screen3" -> Path("permissions", "screen1", "screen2", "screen3")
        else -> null
      }
    }
    else -> {
      error("""unknown regionId=$regionId""")
    }
  }

  public override fun nodeType(regionId: RegionId, path: Path): Schema.NodeType = when (regionId) {
    regions[0] -> {
      when {
        path == Path("permissions") -> Schema.NodeType.Flow
        path == Path("permissions", "screen1") -> Schema.NodeType.Screen
        path == Path("permissions", "screen1", "screen2") -> Schema.NodeType.Screen
        path == Path("permissions", "screen1", "screen2", "screen3") -> Schema.NodeType.Screen
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
