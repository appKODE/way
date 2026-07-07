package ru.kode.way

import kotlin.jvm.JvmInline
import kotlin.random.Random

@JvmInline
value class Path(val segments: List<Segment>) {

  constructor(segment: Segment) : this(listOf(segment))
  constructor(
    segmentId: String,
    vararg segmentIds: String,
  ) : this(
    buildList {
      add(Segment(segmentId))
      segmentIds.forEach { add(Segment(it)) }
    },
  )

  companion object;

  init {
    check(segments.isNotEmpty()) { "path must have at least one segment" }
    check(segments.all { it.name.isNotBlank() }) {
      "all path segments ids must be non-blank in path=$this"
    }
  }

  override fun toString(): String = segments.joinToString(".") { it.name }

  val length get() = segments.size
}

@JvmInline
value class Segment(val id: String) {
  companion object {
    private const val HEX_ALPHABET = "0123456789abcdef"
    private const val RANDOM_ID_LENGTH = 32

    /**
     * A [Segment] with a random 32-char hex id. Used for synthetic segments that need a unique id
     * but carry no schema meaning. Not cryptographically secure — 32 hex chars (128 bits) is for
     * collision avoidance, not unpredictability. Uses [kotlin.random.Random] rather than
     * `java.util.UUID` so the code stays in KMP `commonMain`.
     */
    fun random(): Segment = Segment(
      buildString(RANDOM_ID_LENGTH) {
        repeat(RANDOM_ID_LENGTH) { append(HEX_ALPHABET[Random.nextInt(HEX_ALPHABET.length)]) }
      },
    )
  }
}

val Segment.name: String get() {
  return id.takeWhile { it != '@' }
}

fun Path.tail(): Path = Path(segments.drop(1))

fun Path.head(): Segment = segments.first()

fun Path.firstSegment(): Segment = segments.first()

fun Path.lastSegment(): Segment = segments.last()

fun Path.drop(count: Int): Path {
  if (count == 0) return this
  return Path(segments.drop(count))
}

fun Path.dropLast(count: Int): Path {
  if (count == 0) return this
  return Path(segments.dropLast(count))
}

fun Path.take(count: Int): Path = Path(segments.take(count))

/**
 * Re-anchors this absolute path to be relative to a schema mounted at [schemaPath], keeping the
 * schema's own root as segment 0. Equivalent to `drop(schemaPath.length - 1)`: the `-1` retains the
 * schema root segment while stripping the ancestor segments above the schema mount point.
 */
internal fun Path.relativeToSchema(schemaPath: Path): Path = drop(schemaPath.length - 1)

/** Returns a copy of this path with its first segment replaced by [rootSegment]. */
internal fun Path.reRootAt(rootSegment: Segment): Path = Path(listOf(rootSegment) + segments.drop(1))

/** True when this path is a schema root — a single-segment path (the SCXML `<scxml>` root element). */
internal val Path.isSchemaRoot: Boolean get() = length == 1

fun Path.startsWith(other: Path): Boolean {
  if (this.length < other.length) {
    return false
  }
  for (i in 0 until other.length) {
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
  for (i in 0 until other.length) {
    if (this.segments[this.segments.lastIndex - i] != other.segments[other.segments.lastIndex - i]) {
      return false
    }
  }
  return true
}

/**
 * Like [endsWith], but the FIRST segment of [other] is compared by [Segment.name] only — the
 * `@<graphId>:<file>` disambiguator on that one segment may differ across a schema mount that
 * spans two Gradle modules (parent module's codegen stamps its own identity on the boundary
 * segment, leaf module's codegen stamps the leaf's). Every remaining segment in [other] still
 * requires strict `Segment.id` equality.
 *
 * `internal` because the only legitimate caller is `resolveRegionId`'s fallback lookup (see
 * BackRouting.kt), where the boundary tolerance has a well-defined meaning: the region id was
 * supplied as a leaf schema's `*RegionId` constant and the candidate is the runtime-constructed
 * absolute regionId. Other path comparisons must keep strict id equality.
 */
internal fun Path.endsWithSchemaLocal(other: Path): Boolean {
  if (other.length == 0 || this.length < other.length) return false
  val boundaryIndex = this.segments.lastIndex - (other.length - 1)
  if (this.segments[boundaryIndex].name != other.segments[0].name) return false
  for (i in 1 until other.length) {
    if (this.segments[boundaryIndex + i] != other.segments[i]) return false
  }
  return true
}

fun Path.prepend(path: Path): Path = Path(path.segments + segments)

fun Path.prepend(segment: Segment): Path = Path(
  buildList(segments.size + 1) {
    add(segment)
    addAll(segments)
  },
)

fun Path.append(path: Path): Path = Path(this.segments + path.segments)

fun Path.removePrefix(path: Path): Path {
  require(this != path) { "removePrefix: prefix equals the full path \"$path\"; result would be empty" }
  return if (this.startsWith(path)) this.drop(path.segments.size) else this
}

/**
 * Generate a path sequence leading up to this path:
 * ```
 * app.permissions.intro → [app, app.permissions, app.permissions.intro]
 * ```
 */
fun Path.toSteps(): Sequence<Path> = segments.indices.asSequence().map { i -> this.take(i + 1) }

/**
 * Generate a path sequence leading from this path up to its root
 * ```
 * app.permissions.intro → [ app.permissions.intro, app.permissions, app ]
 * ```
 */
fun Path.toStepsReversed(): Sequence<Path> = segments.indices.reversed().asSequence().map { i -> this.take(i + 1) }
