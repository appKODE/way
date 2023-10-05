package ru.kode.way

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.property.Arb
import io.kotest.property.checkAll
import ru.kode.way.generator.path

class PathTest : ShouldSpec() {
  init {
    should("have startWith working one way only") {
      checkAll(Arb.path(), Arb.path()) { path: Path, suffix: Path ->
        val other = path.append(suffix)
        other.startsWith(path).shouldBeTrue()
        path.startsWith(other).shouldBeFalse()
      }
    }

    should("start with itself") {
      checkAll(Arb.path()) { path: Path ->
        path.startsWith(path).shouldBeTrue()
      }
    }

    should("have endsWith working one way only") {
      checkAll(Arb.path(), Arb.path()) { path: Path, prefix: Path ->
        val other = path.prepend(prefix)
        other.endsWith(path).shouldBeTrue()
        path.endsWith(other).shouldBeFalse()
      }
    }

    should("ends with itself") {
      checkAll(Arb.path()) { path: Path ->
        path.endsWith(path).shouldBeTrue()
      }
    }
  }
}
