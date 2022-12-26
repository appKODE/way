package ru.kode.way

import kotlin.jvm.JvmInline

@JvmInline
value class RegionId(val path: Path) {
  override fun toString(): String {
    return path.toString()
  }
}
