package ru.kode.way

data class TestTransitionSpec(
  val event: String,
  val targetScreen: String?,
  val targetFlow: String?,
)

fun tr_s(on: String, target: String): TestTransitionSpec {
  return TestTransitionSpec(event = on, targetScreen = target, targetFlow = null)
}

fun tr_f(on: String, target: String): TestTransitionSpec {
  return TestTransitionSpec(event = on, targetScreen = null, targetFlow = target)
}
