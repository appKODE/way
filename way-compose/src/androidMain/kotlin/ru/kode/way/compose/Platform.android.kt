package ru.kode.way.compose

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

internal actual fun logWarn(tag: String, message: String) {
  Log.w(tag, message)
}

internal actual fun logDebug(tag: String, message: String) {
  Log.d(tag, message)
}

@Composable
internal actual fun isDebuggable(): Boolean =
  LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
