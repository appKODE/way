package ru.kode.way.compose

import androidx.compose.runtime.Composable

internal expect fun logWarn(tag: String, message: String)

internal expect fun logDebug(tag: String, message: String)

@Composable
internal expect fun isDebuggable(): Boolean
