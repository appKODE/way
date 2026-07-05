package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

val EVENT = ClassName(LIBRARY_PACKAGE, "Event")
val FLOW_NODE = ClassName(LIBRARY_PACKAGE, "FlowNode")
val FLOW_TARGET = ClassName(LIBRARY_PACKAGE, "FlowTarget")
val FLOW_TRANSITION = ClassName(LIBRARY_PACKAGE, "FlowTransition")
val HISTORY_TARGET = ClassName(LIBRARY_PACKAGE, "HistoryTarget")
val NODE = ClassName(LIBRARY_PACKAGE, "Node")
val NODE_BUILDER = ClassName(LIBRARY_PACKAGE, "NodeBuilder")
val PARALLEL_FLOW_NODE = ClassName(LIBRARY_PACKAGE, "ParallelFlowNode")
val PATH = ClassName(LIBRARY_PACKAGE, "Path")
val REGION_ID = ClassName(LIBRARY_PACKAGE, "RegionId")
val SCHEMA = ClassName(LIBRARY_PACKAGE, "Schema")
val SCREEN_NODE = ClassName(LIBRARY_PACKAGE, "ScreenNode")
val SCREEN_TARGET = ClassName(LIBRARY_PACKAGE, "ScreenTarget")
val SEGMENT = ClassName(LIBRARY_PACKAGE, "Segment")
val TARGET = ClassName(LIBRARY_PACKAGE, "Target")

/**
 * Converts a raw DOT type string (e.g. "kotlin.String" or "kotlin.String?") into a KotlinPoet
 * [TypeName]. A trailing '?' marks the type nullable. Non-null types are unchanged.
 */
internal fun parseTypeName(rawType: String): TypeName {
  val trimmed = rawType.trim()
  return if (trimmed.endsWith("?")) {
    ClassName.bestGuess(trimmed.dropLast(1).trim()).copy(nullable = true)
  } else {
    ClassName.bestGuess(trimmed)
  }
}
