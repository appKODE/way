package ru.kode.test.app.scheme

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

  public override fun targets(regionId: RegionId): Map<Segment, Path> = emptyMap()

  public override fun nodeType(regionId: RegionId, path: Path): Schema.NodeType =
      Schema.NodeType.Flow
}
