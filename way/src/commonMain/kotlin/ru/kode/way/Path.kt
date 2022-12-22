package ru.kode.way

@JvmInline
value class Path(val segments: List<Segment>) {

  constructor(segment: Segment): this(listOf(segment))
  constructor(segmentName: String, vararg segmentNames: String): this(listOf(segmentName, *segmentNames).map(::Segment))

  companion object;

  init {
    check(segments.isNotEmpty()) { "path must have at least one segment" }
    check(segments.all { it.name.isNotBlank() }) { "all path segments must be non-blank in path=$this" }
  }
}

@JvmInline
value class Segment(val name: String)
