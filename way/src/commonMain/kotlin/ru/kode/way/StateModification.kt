package ru.kode.way

internal data class StateModification(
  val remove: List<Path>,
  val add: List<Path>,
)
