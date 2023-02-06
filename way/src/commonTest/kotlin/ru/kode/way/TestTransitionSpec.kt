package ru.kode.way

data class TestFlowTransitionSpec(
  val event: String,
  val transition: FlowTransition<*>
)

data class TestScreenTransitionSpec(
  val event: String,
  val transition: ScreenTransition
)

fun tr(on: String, target: Target): TestFlowTransitionSpec {
  return TestFlowTransitionSpec(event = on, NavigateTo(target))
}

fun tr(on: String, transition: FlowTransition<*>): TestFlowTransitionSpec {
  return TestFlowTransitionSpec(event = on, transition)
}

fun trs(on: String, target: Target): TestScreenTransitionSpec {
  return TestScreenTransitionSpec(event = on, NavigateTo(target))
}

fun trs(on: String, transition: ScreenTransition): TestScreenTransitionSpec {
  return TestScreenTransitionSpec(event = on, transition)
}
