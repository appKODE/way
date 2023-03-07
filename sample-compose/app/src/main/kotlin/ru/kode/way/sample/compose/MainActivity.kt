package ru.kode.way.sample.compose

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import ru.kode.way.Back
import ru.kode.way.Event
import ru.kode.way.NavigationService
import ru.kode.way.Stay
import ru.kode.way.compose.NodeHost
import ru.kode.way.extension.service.LogTransitionsExtensionPoint
import ru.kode.way.sample.compose.app.routing.AppFlow
import ru.kode.way.sample.compose.core.routing.FlowEventSink
import ru.kode.way.sample.compose.di.DaggerAppComponent
import ru.kode.way.sample.compose.ui.theme.WayTheme

class MainActivity : ComponentActivity() {
  @OptIn(ExperimentalAnimationApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val eventSink = object : FlowEventSink {
      lateinit var target: NavigationService<*>
      override fun sendEvent(event: Event) {
        target.sendEvent(event)
      }
    }
    val component = DaggerAppComponent.builder()
      .eventSink(eventSink)
      .build()
    val appFlowComponent = component.appFlowComponent()
    val service = NavigationService(AppFlow.schema, AppFlow.nodeBuilder(appFlowComponent)) { _: Unit ->
      finish()
      Stay
    }
    service.addServiceExtensionPoint(
      LogTransitionsExtensionPoint(logger = { msg -> Log.d("way-sample-compose", msg()) })
    )
    eventSink.target = service
    setContent {
      WayTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
          NodeHost(service)
        }
      }
    }
    onBackPressedDispatcher.addCallback {
      service.sendEvent(Event.Back)
    }
  }
}
