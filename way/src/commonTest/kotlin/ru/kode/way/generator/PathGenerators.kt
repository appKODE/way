package ru.kode.way.generator

import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import ru.kode.way.Path
import ru.kode.way.Segment

fun Arb.Companion.path(): Arb<Path> {
  return Arb.list(Arb.segment(), 1..20).map { Path(it) }
}

fun Arb.Companion.segment(): Arb<Segment> {
  return Arb.string(minSize = 1, maxSize = 10, codepoints = Codepoint.alphanumeric()).map { Segment(it) }
}
