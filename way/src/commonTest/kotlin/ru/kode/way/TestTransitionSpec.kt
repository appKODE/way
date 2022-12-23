package ru.kode.way

data class TestTransitionSpec(
  val event: String,
  val target: String,
)

fun tr(on: String, target: String): TestTransitionSpec {
  return TestTransitionSpec(on, target)
}
