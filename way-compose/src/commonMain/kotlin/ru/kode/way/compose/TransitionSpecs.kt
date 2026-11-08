package ru.kode.way.compose

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import ru.kode.way.Path
import ru.kode.way.startsWith

/**
 * A transition spec for [NodeHost] which tells a push from a pop by the two paths: a target path which continues
 * the source one (`app.login.password` to `app.login.password.otp`) is a push, the reverse is a pop.
 * Any other pair of paths is passed to [ambiguousTransitionResolver], which can inspect `initialState` and
 * `targetState`. The first content of a host appears with [noTransition].
 *
 * If the kind is derived incorrectly for your case, write your own spec instead.
 */
@Composable
fun rememberTransitionSpec(
  ambiguousTransitionResolver: AnimatedContentTransitionScope<Path?>.() -> ContentTransform = {
    pushTransition()
  },
): AnimatedContentTransitionScope<Path?>.() -> ContentTransform {
  val resolver by rememberUpdatedState(ambiguousTransitionResolver)
  return remember {
    {
      val fromNode = initialState
      val toNode = targetState
      when {
        fromNode != null && toNode != null -> {
          if (toNode.length > fromNode.length && toNode.startsWith(fromNode)) {
            pushTransition()
          } else if (toNode.length < fromNode.length && fromNode.startsWith(toNode)) {
            popTransition()
          } else {
            resolver()
          }
        }

        else -> noTransition()
      }
    }
  }
}

fun AnimatedContentTransitionScope<*>.pushTransition(): ContentTransform = (
  slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left) togetherWith
    slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Left)
  )
  .using(sizeTransform = null)

fun AnimatedContentTransitionScope<*>.popTransition(): ContentTransform = (
  slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right) togetherWith
    slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Right)
  )
  .using(sizeTransform = null)

fun AnimatedContentTransitionScope<*>.fadeTransition(): ContentTransform {
  // these timings are taken from AnimatedContent's sources
  return (fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith fadeOut(animationSpec = tween(90)))
    .using(sizeTransform = null)
}

fun AnimatedContentTransitionScope<*>.noTransition(): ContentTransform =
  (EnterTransition.None togetherWith ExitTransition.None).using(sizeTransform = null)
