package ru.kode.way

data class TestTransitionSpec(
  val event: String,
  val targetScreen: String?,
  val targetFlow: String?,
  val finishResult: Any?,
) {
  init {
    check(targetScreen != null || targetFlow != null || finishResult != null) {
      "targetScreen or targetFlow or finishResult must not be empty"
    }
  }
}

fun tr_s(on: String, target: String): TestTransitionSpec {
  return TestTransitionSpec(event = on, targetScreen = target, targetFlow = null, finishResult = null)
}

fun tr_f(on: String, target: String): TestTransitionSpec {
  return TestTransitionSpec(event = on, targetScreen = null, targetFlow = target, finishResult = null)
}

fun tr_finish(on: String, result: Any): TestTransitionSpec {
  return TestTransitionSpec(event = on, targetScreen = null, targetFlow = null, finishResult = result)
}
