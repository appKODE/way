package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class ParseTypeNameTest :
  ShouldSpec({
    should("mark a type with a trailing '?' nullable") {
      val type = parseTypeName("kotlin.String?")
      type.isNullable shouldBe true
      type.copy(nullable = false) shouldBe ClassName.bestGuess("kotlin.String")
    }

    should("keep a type without '?' non-null") {
      val type = parseTypeName("kotlin.String")
      type.isNullable shouldBe false
      type shouldBe ClassName.bestGuess("kotlin.String")
    }

    should("support fully-qualified nullable types") {
      val type = parseTypeName("java.nio.Charset?")
      type.isNullable shouldBe true
      type.copy(nullable = false) shouldBe ClassName.bestGuess("java.nio.Charset")
    }

    should("tolerate surrounding whitespace") {
      parseTypeName("  kotlin.String?  ").isNullable shouldBe true
      parseTypeName(" kotlin.String ").isNullable shouldBe false
    }
  })
