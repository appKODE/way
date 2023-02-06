package ru.kode.way.sample.compose.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import ru.kode.way.Event
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

@Composable
fun <E : Event> SampleStubScreen(
  title: String,
  sendEvent: (E) -> Unit,
  eventsClass: KClass<E>,
  eventFilter: (KClass<out E>) -> Boolean = { true },
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(title)
    val subclasses = remember { eventsClass.sealedSubclasses }
    subclasses.filter(eventFilter).forEach { kClass ->
      Button(
        onClick = {
          val event = if (kClass.objectInstance != null) {
            kClass.objectInstance!!
          } else {
            kClass.primaryConstructor!!.call()
          }
          sendEvent(event)
        }
      ) {
        Text(text = kClass.simpleName!!)
      }
    }
  }
}
