package ru.kode.way.sample.compose.permissions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import ru.kode.way.sample.compose.permissions.ui.routing.PermissionsFlowEvent

@Composable
fun IntroScreen(sendEvent: (PermissionsFlowEvent) -> Unit) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(text = "Permissions Intro")
    Button(onClick = { sendEvent(PermissionsFlowEvent.IntroDone) }) {
      Text(text = "Continue")
    }
  }
}
