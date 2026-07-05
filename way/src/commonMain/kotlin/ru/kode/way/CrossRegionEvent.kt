package ru.kode.way

/**
 * Marks an [Event] type that is intended to bubble through a child [FlowNode] and be handled by
 * a parent [ParallelFlowNode] (typically to switch focused regions or coordinate cross-tab navigation).
 *
 * Child FlowNodes that receive a `@CrossRegionEvent`-annotated event MUST return [Ignore] so it
 * reaches the parallel parent. A child that handles the event with any other transition swallows
 * it, silently breaking the intended cross-region routing.
 *
 * This release ships the annotation as documentation only — it surfaces intent in the source and
 * gives reviewers a hook to grep for. A subsequent release will add a codegen-time check that
 * warns when a child flow's generated transition matches such an event with a non-`Ignore` return
 * (the warning becomes an error once consumers have had a release to migrate).
 *
 * Example:
 * ```kotlin
 * sealed interface HomeFlowEvent : Event {
 *   @CrossRegionEvent
 *   data object ShowProfileRequested : HomeFlowEvent
 * }
 * ```
 */
@kotlin.annotation.Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class CrossRegionEvent
