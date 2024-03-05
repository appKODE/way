package ru.kode.way

object BackEvent : Event

val Event.Companion.Back get() = BackEvent

internal data class InitEvent(val payload: Any?) : Event
internal data class RootFinishRequestEvent(val result: Any) : Event

internal object DoneEvent : Event
internal val Event.Companion.Done get() = DoneEvent
