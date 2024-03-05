package ru.kode.way

data class TestFlowTransitionSpec(
  val eventMatcher: (Event) -> Boolean,
  val transition: FlowTransition<*>
)

data class TestScreenTransitionSpec(
  val eventMatcher: (Event) -> Boolean,
  val transition: ScreenTransition
)

fun tr(on: String, target: Target): TestFlowTransitionSpec {
  return TestFlowTransitionSpec(eventMatcher = { it is TestEvent && it.name == on }, NavigateTo(target))
}

fun tr(on: String, transition: FlowTransition<*>): TestFlowTransitionSpec {
  return TestFlowTransitionSpec(eventMatcher = { it is TestEvent && it.name == on }, transition)
}

inline fun <reified E : Event> tr(target: Target): TestFlowTransitionSpec {
  return TestFlowTransitionSpec(eventMatcher = { it is E }, NavigateTo(target))
}

inline fun <reified E : Event> tr(transition: FlowTransition<*>): TestFlowTransitionSpec {
  return TestFlowTransitionSpec(eventMatcher = { it is E }, transition)
}

fun trs(on: String, target: Target): TestScreenTransitionSpec {
  return TestScreenTransitionSpec(eventMatcher = { it is TestEvent && it.name == on }, NavigateTo(target))
}

fun trs(on: String, transition: ScreenTransition): TestScreenTransitionSpec {
  return TestScreenTransitionSpec(eventMatcher = { it is TestEvent && it.name == on }, transition)
}

inline fun <reified E : Event> trs(target: Target): TestScreenTransitionSpec {
  return TestScreenTransitionSpec(eventMatcher = { it is E }, NavigateTo(target))
}

inline fun <reified E : Event> trs(transition: ScreenTransition): TestScreenTransitionSpec {
  return TestScreenTransitionSpec(eventMatcher = { it is E }, transition)
}
