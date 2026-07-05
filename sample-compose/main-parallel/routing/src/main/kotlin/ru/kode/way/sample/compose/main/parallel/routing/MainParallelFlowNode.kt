package ru.kode.way.sample.compose.main.parallel.routing

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.kode.way.Event
import ru.kode.way.FlowTransition
import ru.kode.way.Ignore
import ru.kode.way.ParallelFlowNode
import ru.kode.way.RegionId
import ru.kode.way.Stay
import ru.kode.way.compose.ComposableNode
import ru.kode.way.compose.NodeHost
import javax.inject.Inject

class MainParallelFlowNode @Inject constructor() :
  ParallelFlowNode<Unit>(),
  ComposableNode {
  override val dismissResult = Unit

  // Schema-relative RegionIds. NodeHost(regionId) detects it is rendered inside a parallel
  // (via LocalNodePath) and resolves these to absolute regionIds against the runtime navigation state.
  private val headRegionId: RegionId get() = MainParallelFlow.schema.regions[0]
  private val sheetRegionId: RegionId get() = MainParallelFlow.schema.regions[1]

  override fun transition(event: Event): FlowTransition<Unit> = when (event) {
    is MainParallelChildFinishRequest.Head,
    is MainParallelChildFinishRequest.Sheet,
    -> Stay

    else -> Ignore
  }

  @OptIn(ExperimentalAnimationApi::class)
  @Composable
  override fun Content(modifier: Modifier) {
    // Render both sub-regions side by side. State in each sub-region is preserved independently
    // because each NodeHost(regionId) stays in composition simultaneously.
    Row(modifier = modifier.fillMaxWidth()) {
      Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
        NodeHost(regionId = headRegionId)
      }
      Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
        NodeHost(regionId = sheetRegionId)
      }
    }
  }
}
