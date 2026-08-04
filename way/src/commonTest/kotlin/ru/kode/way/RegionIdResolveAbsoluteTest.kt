package ru.kode.way

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class RegionIdResolveAbsoluteTest : ShouldSpec() {
  init {
    should("return the regionId unchanged when parentPath is null") {
      val regionId = RegionId(Path("exploreFlow"))
      regionId.resolveAbsolute(null) shouldBe regionId
    }

    should("return the regionId unchanged when it is already absolute") {
      val parentPath = Path("appFlow", "mainFlow", "homeFlow")
      val regionId = RegionId(Path("appFlow", "mainFlow", "homeFlow", "exploreFlow"))
      regionId.resolveAbsolute(parentPath) shouldBe regionId
    }

    should("prefix a multi-segment schema-relative regionId with parentPath, dropping its own first segment") {
      val parentPath = Path("appFlow", "mainFlow", "homeFlow")
      // Schema-relative id as generated for a region declared two levels deep in its own schema.
      val regionId = RegionId(Path("homeFlow", "exploreFlow"))
      regionId.resolveAbsolute(parentPath) shouldBe RegionId(Path("appFlow", "mainFlow", "homeFlow", "exploreFlow"))
    }

    should("resolve a length-1 schema-relative regionId to parentPath itself") {
      val parentPath = Path("appFlow", "mainFlow", "homeFlow")
      // A length-1 relative id (e.g. an imported non-parallel schema) has no tail to append.
      val regionId = RegionId(Path("homeFlow"))
      regionId.resolveAbsolute(parentPath) shouldBe RegionId(parentPath)
    }
  }
}
