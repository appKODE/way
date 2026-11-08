package ru.kode.way.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Implement this on any [ru.kode.way.Node] that provides its own Compose rendering. [NodeHost]
 * renders a node by calling [Content] when the node is a [ComposableNode].
 *
 * This interface is `way-compose`'s render opt-in — the seam where presentation attaches to the
 * otherwise UI-agnostic `:way` runtime. Other UI integrations (views, other frameworks) define
 * their own equivalent opt-in interface and host against the same public core contract; see
 * "Building a UI integration" in the README.
 *
 * A [ru.kode.way.ParallelFlowNode] implements this too: [Content] renders the full parallel layout
 * (tab bar, pager, drawer + content, …) and calls [NodeHost] with a [ru.kode.way.RegionId] inside it
 * to render each sub-region's screen stack. To find sub-region ids, use the generated
 * `val <name>RegionId` constants from the schema/NodeBuilder rather than hardcoding path strings.
 *
 * Example — tab bar with state preservation:
 * ```
 * override fun Content(modifier: Modifier) {
 *   Scaffold(modifier = modifier, bottomBar = { TabBar(currentTab) }) { padding ->
 *     listOf(homeRegionId, exploreRegionId).forEach { regionId ->
 *       key(regionId) {
 *         Box(if (regionId == currentTab) Modifier.padding(padding) else Modifier.size(0.dp)) {
 *           NodeHost(regionId)  // kept in composition → state preserved across tab switches
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 */
interface ComposableNode {
  @Composable
  fun Content(modifier: Modifier)
}
