package ru.kode.way

/**
 * Why [NavigationService] dropped an event instead of applying its transition.
 * Reported through [ServiceExtensionPoint.onEventDropped] and [EventDroppedException].
 */
sealed interface DropReason {
  /**
   * The event resolved to a target under the parameterized node at [path], which is not alive and has no
   * payload to be rebuilt with. Typically a short target (no ancestor arguments) sent after the user has
   * already left its flow. The drop is all-or-nothing: when one target of a multi-target [NavigateTo], or one region
   * receiving a broadcast event, misses a payload, no part of the transition is applied.
   */
  data class MissingPayload(val path: Path) : DropReason

  /**
   * The event was sent through an [EventSink] of the node at [path], and that node is no longer alive (or was
   * recreated) by the time the event was dispatched.
   */
  data class StaleSource(val path: Path) : DropReason
}

/**
 * The only way to send events. Take the nearest sink: in UI `LocalEventSink` (way-compose), in a node its
 * [ru.kode.way.extension.node.hook.BaseFlowNode.eventSink] / [ru.kode.way.extension.node.hook.BaseScreenNode.eventSink],
 * in a presenter the sink of its screen passed from the node, outside navigation (Activity back, a deep link, a push)
 * the [NavigationService] itself, which is the root sink.
 *
 * A node's sink (see [NavigationService.eventSink]) sends on behalf of one node instance: a screen's sink starts at
 * the screen, a flow's or parallel's at the active leaves under it; the event bubbles up through the node, reaches
 * every parallel enclosing the node and bubbles up through each parallel's ancestors, like [NavigationService.send].
 * Same threading rules as [NavigationService].
 */
fun interface EventSink {
  fun send(event: Event)
}

/**
 * An event sent through a node's [EventSink]. Only the service's internal dispatch unwraps it, so it never reaches
 * nodes, extension points or listeners.
 */
internal class SourcedEvent(val event: Event, val source: Path, val generation: Long) : Event {
  override fun toString(): String = "SourcedEvent($event, source=$source)"
}

/**
 * Thrown while resolving or building nodes when the parameterized node at [path] has no payload.
 * [NavigationService] rolls the transition back and drops the event (see [DropReason.MissingPayload]).
 */
class MissingPayloadException(val path: Path) : IllegalStateException("no payload for \"$path\"")

/**
 * Thrown from [EventSink.send] when [NavigationService.strictEventDropping] is `true` and
 * [event] is dropped for [reason]. Navigation state is left as it was before [event].
 */
class EventDroppedException(val event: Event, val reason: DropReason, cause: Throwable? = null) :
  IllegalStateException("event $event was dropped: $reason", cause)

/**
 * Returns the first not-alive parameterized node on the way to any of [resolved]'s target paths which has no
 * payload neither in [state] nor in [resolved], or `null` when every such node can be built. Region roots, intermediate
 * parallels and the schema root are skipped: they live as long as their region and are built from the start payload.
 */
internal fun findMissingPayload(schema: Schema, state: NavigationState, resolved: ResolvedTransition): Path? {
  val configuration = computeConfiguration(state)
  val regionRoots = (state._regions.keys + resolved.targetPaths.keys).mapTo(mutableSetOf()) { it.path }
  regionRoots += state._intermediateParallels.keys
  return resolved.targetPaths.values.asSequence()
    .flatMap { it.toSteps().drop(1) }
    .filter { it !in configuration && it !in regionRoots }
    .filter { it !in state._payloads && it !in resolved.payloads }
    .firstOrNull { isParameterized(schema, it) }
}

private fun isParameterized(rootSchema: Schema, path: Path): Boolean {
  val (activeSchema, schemaPath) = findParentSchema(rootSchema, path, inclusive = true)
  val relativePath = path.relativeToSchema(schemaPath)
  val regionId = owningRegionInSchema(activeSchema, schemaPath, path)
  return activeSchema.isParameterized(regionId, relativePath, rootSegmentAlias = relativePath.firstSegment())
}
