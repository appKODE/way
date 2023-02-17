package ru.kode.way

object BackEvent : Event

val Event.Companion.Back get() = BackEvent

internal object InitEvent : Event
internal val Event.Companion.Init get() = InitEvent

internal object DoneEvent : Event
internal val Event.Companion.Done get() = DoneEvent
