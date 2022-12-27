package ru.kode.way

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.shouldBe

class TestSchemaBuildTest : ShouldSpec({
  should("generate complex scheme 1") {
    val sut = TestSchema.fromIndentedText(
      regionId = "app",
      """
          app
            permissions
              pe_intro
              request
            profile
              pr_intro
      """.trimIndent()
    )

    val regionId = RegionId(Path("app"))
    sut.regions shouldHaveSingleElement regionId

    sut.children(regionId).map { it.name } shouldContainInOrder listOf(
      "app", "permissions", "pe_intro", "request", "profile", "pr_intro"
    )
    sut.children(regionId, Segment("app")).map { it.name } shouldContainInOrder listOf(
      "permissions", "pe_intro", "request", "profile", "pr_intro"
    )
    sut.children(regionId, Segment("permissions")).map { it.name } shouldContainInOrder listOf(
      "pe_intro", "request"
    )
    sut.children(regionId, Segment("profile")).map { it.name } shouldContainInOrder listOf(
      "pr_intro"
    )
    val targets = sut.targets(regionId)
    targets[Segment("app")].toString() shouldBe "app"
    targets[Segment("permissions")].toString() shouldBe "app.permissions"
    targets[Segment("pe_intro")].toString() shouldBe "app.permissions.pe_intro"
    targets[Segment("request")].toString() shouldBe "app.permissions.request"
    targets[Segment("profile")].toString() shouldBe "app.profile"
    targets[Segment("pr_intro")].toString() shouldBe "app.profile.pr_intro"
  }

  should("generate complex scheme 2") {
    val sut = TestSchema.fromIndentedText(
      regionId = "app",
      """
          app
            permissions
              pe_intro
                request
            profile
              pr_intro
                main
      """.trimIndent()
    )

    val regionId = RegionId(Path("app"))
    sut.regions shouldHaveSingleElement regionId

    sut.children(regionId).map { it.name } shouldContainInOrder listOf(
      "app", "permissions", "pe_intro", "request", "profile", "pr_intro", "main"
    )
    sut.children(regionId, Segment("app")).map { it.name } shouldContainInOrder listOf(
      "permissions", "pe_intro", "request", "profile", "pr_intro", "main"
    )
    sut.children(regionId, Segment("permissions")).map { it.name } shouldContainInOrder listOf(
      "pe_intro", "request"
    )
    sut.children(regionId, Segment("profile")).map { it.name } shouldContainInOrder listOf(
      "pr_intro", "main"
    )
    val targets = sut.targets(regionId)
    targets[Segment("app")].toString() shouldBe "app"
    targets[Segment("permissions")].toString() shouldBe "app.permissions"
    targets[Segment("pe_intro")].toString() shouldBe "app.permissions.pe_intro"
    targets[Segment("request")].toString() shouldBe "app.permissions.pe_intro.request"
    targets[Segment("profile")].toString() shouldBe "app.profile"
    targets[Segment("pr_intro")].toString() shouldBe "app.profile.pr_intro"
    targets[Segment("main")].toString() shouldBe "app.profile.pr_intro.main"
  }
})
