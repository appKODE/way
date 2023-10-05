package ru.kode.way.compose

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import ru.kode.way.FlowTransition
import ru.kode.way.NavigationService
import ru.kode.way.NavigationState
import ru.kode.way.Node
import ru.kode.way.NodeBuilder
import ru.kode.way.Path
import ru.kode.way.Schema
import ru.kode.way.startsWith

@ExperimentalAnimationApi
@Composable
fun NodeHost(
  service: NavigationService<*>,
  transitionSpec: AnimatedContentTransitionScope<NodeWithPath?>.() -> ContentTransform = defaultTransitionSpec
) {
  LaunchedEffect(service) {
    if (!service.isStarted()) {
      service.start()
    }
  }
  val activeNode by collectActiveNode(service)
  AnimatedContent(
    transitionSpec = transitionSpec,
    targetState = activeNode,
    label = "NodeHost",
  ) { state ->
    if (state != null) {
      val (path, node) = state
      if (node is ComposableNode) {
        (node as ComposableNode).Content(Modifier)
      } else {
        Log.d("way-compose", "didn't find a ComposableNode for \"$path\", rendering an empty content")
        Box() {}
      }
    } else {
      Box() {}
    }
  }
}

@ExperimentalAnimationApi
@Composable
fun <R : Any> NodeHost(schema: Schema, nodeBuilder: NodeBuilder, onFinishRequest: (R) -> FlowTransition<Unit>) {
  val service = remember(schema) { NavigationService(schema, nodeBuilder, onFinishRequest) }
  NodeHost(service)
}

@ExperimentalAnimationApi
val defaultTransitionSpec: AnimatedContentTransitionScope<NodeWithPath?>.() -> ContentTransform = {
  val initial = initialState
  val target = targetState
  when {
    initial != null && target != null -> {
      // A very basic attempt at distinguishing push from pop: check if new target is contained in the
      // old one -> going back
      // This doesn't work sufficiently well though and should be improved
      // (see sample-compose, it has weird transitions)
      // TODO Calculate transitions based on more clever heuristics. They can require looking into Schema to
      //   determine least common parent Flow of two paths and also can query actual alive Nodes for hints on
      //   transitions they desire in ambiguous situations
      if (!(initial.path.length > target.path.length && initial.path.startsWith(target.path))) {
        slideIntoContainer(SlideDirection.Left) togetherWith slideOutOfContainer(SlideDirection.Left)
      } else {
        slideIntoContainer(SlideDirection.Right) togetherWith slideOutOfContainer(SlideDirection.Right)
      }
    }
    // these defaults are taken from AnimatedContent's sources
    else -> (fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith fadeOut(animationSpec = tween(90)))
      .using(sizeTransform = null)
  }
}

@Composable
fun collectActiveNode(service: NavigationService<*>): State<NodeWithPath?> {
  return produceState<NodeWithPath?>(initialValue = null, service) {
    val listener = { s: NavigationState ->
      // TODO figure out how to render multiple regions
      value = s.regions.values.first().let { NodeWithPath(it.active, it.activeNode) }
    }
    service.addTransitionListener(listener)
    awaitDispose {
      service.removeTransitionListener(listener)
    }
  }
}

@Immutable
data class NodeWithPath(
  val path: Path,
  val node: Node,
)
