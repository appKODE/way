package ru.kode.way.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface ComposableNode {
  @Composable
  fun Content(modifier: Modifier)
}
