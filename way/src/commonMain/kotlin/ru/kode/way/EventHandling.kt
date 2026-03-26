package ru.kode.way

inline fun <reified E : Event, R : Any> Event.whenFlowEvent(transition: (E) -> FlowTransition<R>): FlowTransition<R> =
  when (this) {
    is E -> transition(this)
    else -> Ignore
  }

inline fun <reified E : Event> Event.whenScreenEvent(transition: (E) -> ScreenTransition): ScreenTransition =
  when (this) {
    is E -> transition(this)
    else -> Ignore
  }
