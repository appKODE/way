package ru.kode.way

import java.util.UUID
import kotlin.jvm.JvmInline

@JvmInline
value class Path(val segments: List<Segment>) {

  constructor(segment: Segment) : this(listOf(segment))
  constructor(
    segmentId: String,
    vararg segmentIds: String
  ) : this(buildList { add(Segment(segmentId)); segmentIds.forEach { add(Segment(it)) } })

  companion object;

  init {
    check(segments.isNotEmpty()) { "path must have at least one segment" }
    check(segments.all { it.name.isNotBlank() }) {
      "all path segments ids must be non-blank in path=$this"
    }
  }

  override fun toString(): String {
    return segments.joinToString(".") { it.name }
  }

  val length get() = segments.size
}

@JvmInline
value class Segment(val id: String = UUID.randomUUID().toString()) {
  companion object
}

val Segment.name: String get() {
  return id.takeWhile { it != '@' }
}

fun Path.tail(): Path {
  return Path(segments.drop(1))
}

fun Path.head(): Segment {
  return segments.first()
}

fun Path.firstSegment(): Segment {
  return segments.first()
}

fun Path.lastSegment(): Segment {
  return segments.last()
}

fun Path.drop(count: Int): Path {
  if (count == 0) return this
  return Path(segments.drop(count))
}

fun Path.dropLast(count: Int): Path {
  if (count == 0) return this
  return Path(segments.dropLast(count))
}

fun Path.take(count: Int): Path {
  return Path(segments.take(count))
}

fun Path.startsWith(other: Path): Boolean {
  if (this.length < other.length) {
    return false
  }
  for (i in (0..other.length - 1)) {
    if (this.segments[i] != other.segments[i]) {
      return false
    }
  }
  return true
}

fun Path.endsWith(other: Path): Boolean {
  if (this.length < other.length) {
    return false
  }
  for (i in (0..other.length - 1)) {
    if (this.segments[this.segments.lastIndex - i] != other.segments[other.segments.lastIndex - i]) {
      return false
    }
  }
  return true
}

fun Path.prepend(path: Path): Path {
  return Path(path.segments + segments)
}

fun Path.prepend(segment: Segment): Path {
  return Path(
    buildList(segments.size + 1) {
      add(segment)
      addAll(segments)
    }
  )
}

fun Path.append(path: Path): Path {
  return Path(this.segments + path.segments)
}

fun Path.removePrefix(path: Path): Path {
  return if (this.startsWith(path)) this.drop(path.segments.size) else this
}

/**
 * Generate a path sequence leading up to this path:
 * ```
 * app.permissions.intro → [app, app.permissions, app.permissions.intro]
 * ```
 */
fun Path.toSteps(): Sequence<Path> {
  return segments.indices.asSequence().map { i -> this.take(i + 1) }
}

/**
 * Generate a path sequence leading from this path up to its root
 * ```
 * app.permissions.intro → [ app.permissions.intro, app.permissions, app ]
 * ```
 */
fun Path.toStepsReversed(): Sequence<Path> {
  return segments.indices.reversed().asSequence().map { i -> this.take(i + 1) }
}
