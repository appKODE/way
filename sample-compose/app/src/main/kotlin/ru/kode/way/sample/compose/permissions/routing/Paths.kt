package ru.kode.way.sample.compose.permissions.routing

import ru.kode.way.Path
import ru.kode.way.ScreenTarget
import ru.kode.way.Target

// TODO rename to targets
object PermissionsPaths {
  val intro: Target = ScreenTarget(Path("intro"))
  val request: Target = ScreenTarget(Path("intro", "request"))
}

val Path.Companion.permissions get() = PermissionsPaths
