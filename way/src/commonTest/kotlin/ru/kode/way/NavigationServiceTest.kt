package ru.kode.way

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.ShouldSpec

class NavigationServiceTest : ShouldSpec({
  should("throw when accessing state") {
    val sut = NavigationService(object : Schema {})
    shouldThrowAny {
      sut.states
    }
  }
})
