package ru.kode.way.sample.compose.permissions.ui

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.kode.way.Event
import ru.kode.way.ScreenNode
import ru.kode.way.ScreenTransition
import ru.kode.way.Stay
import ru.kode.way.compose.ComposableNode

class RequestScreenNode : ScreenNode<Event>, ComposableNode {
  override fun transition(event: Event): ScreenTransition {
    return Stay
  }

  @Composable
  override fun Content(modifier: Modifier) {
    Text(text = "Request")
  }
}
