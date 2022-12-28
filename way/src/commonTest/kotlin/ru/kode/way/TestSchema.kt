package ru.kode.way

class TestSchema private constructor(
  override val regions: List<RegionId>,
  private val children: Map<RegionId, Map<Segment, Set<Segment>>>,
  private val targets: Map<RegionId, Map<Segment, Path>>,
  private val nodeTypes: Map<RegionId, Map<Segment, Schema.NodeType>>,
) : Schema {
  companion object {
    fun fromIndentedText(regionId: String, text: String): Schema {
      val lines = text.lines().map { it.replace(TEST_SCHEMA_FLOW_MARKER, "") }
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
      val nodeTypes = text.lines().associateBy(
        { Segment(it.trim().removePrefix(TEST_SCHEMA_FLOW_MARKER)) },
        { if (it.trim().startsWith(TEST_SCHEMA_FLOW_MARKER)) Schema.NodeType.Flow else Schema.NodeType.Screen }
      )
      return TestSchema(
        regions = listOf(rId),
        children = mapOf(
          rId to children,
        ),
        targets = mapOf(
          rId to targets
        ),
        nodeTypes = mapOf(
          rId to nodeTypes
        ),
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

  override fun nodeType(regionId: RegionId, path: Path): Schema.NodeType {
    return nodeTypes.getValue(regionId)[path.segments.last()]
      ?: error("no node for $path in ${nodeTypes.getValue(regionId)}")
  }
}

private const val TEST_SCHEMA_FLOW_MARKER = "f:"
