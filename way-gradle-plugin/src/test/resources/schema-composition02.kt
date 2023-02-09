package ru.kode.test.app.schema

import kotlin.collections.List
import kotlin.collections.Set
import ru.kode.way.Path
import ru.kode.way.RegionId
import ru.kode.way.Schema
import ru.kode.way.Segment
import ru.kode.way.append
import ru.kode.way.removePrefix
import ru.kode.way.startsWith

public class TestAppSchema(
  private val loginSchema: Schema,
  private val mainSchema: Schema,
) : Schema {
  public override val regions: List<RegionId> = listOf(RegionId(Path("app")))

  public override fun children(regionId: RegionId): Set<Segment> = emptySet()

  public override fun children(regionId: RegionId, segment: Segment): Set<Segment> = emptySet()

  public override fun target(regionId: RegionId, segment: Segment): Path? = when (regionId) {
    regions[0] -> {
      when(segment.name) {
        "app" -> Path("app")
        "page1" -> Path("app","page1")
        "page2" -> Path("app","page1","page2")
        else ->  {
          loginSchema.target(loginSchema.regions.first(), segment)
          ?.let { Path("app","page1","page2").append(it) }
          ?: mainSchema.target(mainSchema.regions.first(), segment)
          ?.let { Path("app","page1","page2","login").append(it) }
        }
      }
    }
    else -> {
      error("""unknown regionId=$regionId""")
    }
  }

  public override fun nodeType(regionId: RegionId, path: Path): Schema.NodeType = when (regionId) {
    regions[0] -> {
      when {
        path == Path("app") -> Schema.NodeType.Flow
        path == Path("app","page1") -> Schema.NodeType.Screen
        path == Path("app","page1","page2") -> Schema.NodeType.Screen
        path.startsWith(Path("app","page1","page2","login","main")) ->
            mainSchema.nodeType(mainSchema.regions.first(),
            path.removePrefix(Path("app","page1","page2","login")))
        path.startsWith(Path("app","page1","page2","login")) ->
            loginSchema.nodeType(loginSchema.regions.first(),
            path.removePrefix(Path("app","page1","page2")))
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
