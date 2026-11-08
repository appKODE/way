package ru.kode.way.compose

import androidx.compose.runtime.Composable
import kotlin.experimental.ExperimentalNativeApi

internal actual fun logWarn(tag: String, message: String) {
  println("W/$tag: $message")
}

internal actual fun logDebug(tag: String, message: String) {
  println("D/$tag: $message")
}

@OptIn(ExperimentalNativeApi::class)
@Composable
internal actual fun isDebuggable(): Boolean = Platform.isDebugBinary
