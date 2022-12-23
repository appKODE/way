package ru.kode.way.sample.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import ru.kode.way.Path
import ru.kode.way.Schema
import ru.kode.way.compose.NodeHost
import ru.kode.way.sample.compose.app.routing.di.AppFlowComponentImpl
import ru.kode.way.sample.compose.ui.theme.WayTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val component = AppFlowComponentImpl()
    setContent {
      WayTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
          val schema = remember {
            object : Schema {
              override val regions: List<Path> = listOf(Path("app"))
            }
          }
          NodeHost(schema = schema, nodeBuilder = component.nodeBuilder())
        }
      }
    }
  }
}
