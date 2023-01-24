package ru.kode.way

data class TestTransitionSpec(
  val event: String,
  val target: Target? = null,
  val finishResult: Any? = null,
) {
  init {
    check(target != null || finishResult != null) {
      "target finishResult must not be null"
    }
  }
}

fun tr(on: String, target: Target): TestTransitionSpec {
  return TestTransitionSpec(event = on, target)
}

fun tr_finish(on: String, result: Any): TestTransitionSpec {
  return TestTransitionSpec(event = on, finishResult = result)
}
