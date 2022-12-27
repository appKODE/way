package ru.kode.way

class TestSchema(
  override val regions: List<RegionId>,
  private val children: Map<RegionId, Map<Segment, Set<Segment>>>,
  private val targets: Map<RegionId, Map<Segment, Path>>,
) : Schema {
  companion object {
    fun fromIndentedText(regionId: String, text: String): Schema {
      val lines = text.lines()
      check(
        lines.run {
          mapTo(mutableSetOf()) { it.trim() }.size == size
        }
      ) {
        "all nodes must be unique, got duplicates:" +
          " ${lines.filter { l -> lines.count { l == it } > 1 }.map { it.trim() }.toSet()}"
      }
      val lineNo = lines.mapIndexed { index, s -> s.trim() to index }.toMap()
      val indents = lines.associateBy(
        { line -> line.trim() },
        { line -> line.trimEnd().count { it.isWhitespace() } }
      )
      val rId = RegionId(Path(regionId))
      val targets = indents.keys
        .associateBy(
          { seg -> Segment(seg) },
          { seg ->
            val segIndent = indents.getValue(seg)
            var parents = indents
              .filter { (line, indent) -> indent < segIndent && lineNo.getValue(line) < lineNo.getValue(seg) }
              .keys
              .toList()
            parents = parents
              .filterIndexed { index, s -> parents.drop(index + 1).find { indents[it] == indents[s] } == null }
            Path(parents.map { Segment(it) } + Segment(seg))
          }
        )

      val children = indents.keys
        .associateBy(
          { seg -> Segment(seg) },
          { seg ->
            val segIndent = indents.getValue(seg)
            indents.filter { (_, indent) -> indent > segIndent }.keys.mapTo(mutableSetOf()) { Segment(it) }
          }
        )
      return TestSchema(
        regions = listOf(rId),
        children = mapOf(
          rId to children,
        ),
        targets = mapOf(
          rId to targets
        )
      )
    }
  }

  override fun children(regionId: RegionId): Set<Segment> {
    return children.getValue(regionId).flatMapTo(mutableSetOf()) { setOf(it.key) + it.value }
  }

  override fun children(regionId: RegionId, segment: Segment): Set<Segment> {
    return children.getValue(regionId)[segment].orEmpty()
  }

  override fun targets(regionId: RegionId): Map<Segment, Path> {
    return targets.getValue(regionId)
  }
}
