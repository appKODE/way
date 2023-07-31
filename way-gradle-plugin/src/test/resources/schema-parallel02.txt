package ru.kode.test.app.schema

import kotlin.collections.List
import kotlin.collections.Set
import ru.kode.way.Path
import ru.kode.way.RegionId
import ru.kode.way.Schema
import ru.kode.way.Segment

public class TestAppSchema : Schema {
  public override val regions: List<RegionId> = listOf(RegionId(Path("main", "one")),
      RegionId(Path("main", "two")), RegionId(Path("main", "one", "alpha")), RegionId(Path("main",
      "one", "beta")))

  public override fun children(regionId: RegionId): Set<Segment> = emptySet()

  public override fun children(regionId: RegionId, segment: Segment): Set<Segment> = emptySet()

  public override fun target(regionId: RegionId, segment: Segment): Path? = when (regionId) {
    regions[0] -> {
      when(segment.name) {
        "one" -> Path("main", "one")
        "beta" -> Path("main", "one", "beta")
        "introb1" -> Path("main", "one", "beta", "introb1")
        "alpha" -> Path("main", "one", "alpha")
        "introa1" -> Path("main", "one", "alpha", "introa1")
        else -> null
      }
    }
    regions[1] -> {
      when(segment.name) {
        "two" -> Path("main", "two")
        "intro1" -> Path("main", "two", "intro1")
        else -> null
      }
    }
    regions[2] -> {
      when(segment.name) {
        "alpha" -> Path("main", "one", "alpha")
        "introa1" -> Path("main", "one", "alpha", "introa1")
        else -> null
      }
    }
    regions[3] -> {
      when(segment.name) {
        "beta" -> Path("main", "one", "beta")
        "introb1" -> Path("main", "one", "beta", "introb1")
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
        path == Path("main") -> Schema.NodeType.Parallel
        path == Path("main", "one") -> Schema.NodeType.Parallel
        path == Path("main", "two") -> Schema.NodeType.Flow
        path == Path("main", "one", "alpha") -> Schema.NodeType.Flow
        path == Path("main", "one", "beta") -> Schema.NodeType.Flow
        path == Path("main", "one", "alpha", "introa1") -> Schema.NodeType.Screen
        path == Path("main", "one", "beta", "introb1") -> Schema.NodeType.Screen
        path == Path("main", "two", "intro1") -> Schema.NodeType.Screen
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
