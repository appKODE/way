package ru.kode.way.gradle

import com.android.build.api.variant.ComponentIdentity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import java.io.File
import java.nio.file.Files

private fun applyWayPluginToPlainKotlinJvmProject() {
  val project = ProjectBuilder.builder().build()
  project.pluginManager.apply("org.jetbrains.kotlin.jvm")
  project.pluginManager.apply(WayPlugin::class.java)
  (project as org.gradle.api.internal.project.ProjectInternal).evaluate()
}

private data class FakeComponentIdentity(
  override val name: String,
  override val buildType: String?,
  override val productFlavors: List<Pair<String, String>>,
  override val flavorName: String,
) : ComponentIdentity

private fun createTempSourceSetDir(): File = Files.createTempDirectory("way-source-set").toFile()

class WayPluginSourceResolutionTest :
  ShouldSpec({
    should("resolve way dir from source set root directory") {
      val sourceSetRoot = File("/tmp/proj-android/feature/login/routing/src/main")

      sourceSetRoot.resolveWaySourceDir("main") shouldBe
        File("/tmp/proj-android/feature/login/routing/src/main/way")
    }

    should("resolve way dir from kotlin and java source directories") {
      val kotlinSource = File("/tmp/proj-android/feature/login/routing/src/main/kotlin")
      val javaSource = File("/tmp/proj-android/feature/login/routing/src/main/java")

      kotlinSource.resolveWaySourceDir("main") shouldBe
        File("/tmp/proj-android/feature/login/routing/src/main/way")
      javaSource.resolveWaySourceDir("main") shouldBe
        File("/tmp/proj-android/feature/login/routing/src/main/way")
    }

    should("treat only android and unit tests as test source sets") {
      isWayTestSourceSet("test") shouldBe true
      isWayTestSourceSet("androidTest") shouldBe true
      isWayTestSourceSet("testDebug") shouldBe true
      isWayTestSourceSet("androidTestDebug") shouldBe true

      isWayTestSourceSet("main") shouldBe false
      isWayTestSourceSet("debug") shouldBe false
      isWayTestSourceSet("release") shouldBe false
    }

    should("collect resolved android source dirs when they are valid") {
      val kotlinDir = File("/tmp/proj-android/feature/login/routing/custom/kotlin")
      val javaDir = File("/tmp/proj-android/feature/login/routing/custom/java")

      collectAndroidSourceDirectories(
        kotlinSourceDirectories = listOf(kotlinDir),
        javaSourceDirectories = listOf(javaDir),
      ) shouldBe listOf(kotlinDir, javaDir)
    }

    should("return empty source dirs when resolved dirs are unavailable") {
      collectAndroidSourceDirectories(
        kotlinSourceDirectories = emptyList(),
        javaSourceDirectories = null,
      ) shouldBe emptyList()
    }

    should("register generated dir in kotlin main source set without reflection") {
      val project = ProjectBuilder.builder().build()
      project.pluginManager.apply("org.jetbrains.kotlin.jvm")

      val kotlinExtension = project.extensions.findByType(KotlinProjectExtension::class.java)
      val generatedDir = project.layout.buildDirectory.dir("generated/way/code/main")
      val taskProvider = project.tasks.register("generateWayClasses", GenerateClassesTask::class.java) { task ->
        task.outputDirectory.set(generatedDir)
      }

      registerGeneratedDirInKotlinMainSourceSet(kotlinExtension, taskProvider)

      kotlinExtension?.sourceSets
        ?.getByName("main")
        ?.kotlin
        ?.srcDirs
        ?.contains(generatedDir.get().asFile) shouldBe true
    }

    should("do nothing when kotlin extension is null while registering generated dir") {
      val project = ProjectBuilder.builder().build()
      val taskProvider = project.tasks.register("generateWayClasses", GenerateClassesTask::class.java)

      runCatching {
        registerGeneratedDirInKotlinMainSourceSet(null, taskProvider)
      }.isSuccess shouldBe true
    }

    should("compute main, build type and flavor source set names for a flavorless variant") {
      val identity = FakeComponentIdentity(
        name = "debug",
        buildType = "debug",
        productFlavors = emptyList(),
        flavorName = "",
      )

      variantSourceSetNamesInPriorityOrder(identity) shouldBe listOf("main", "debug")
    }

    should("compute full main, flavor, buildType, variant-specific chain for a flavored variant") {
      val identity = FakeComponentIdentity(
        name = "googleDebug",
        buildType = "debug",
        productFlavors = listOf("tier" to "google"),
        flavorName = "google",
      )

      variantSourceSetNamesInPriorityOrder(identity) shouldBe
        listOf("main", "google", "debug", "googleDebug")
    }

    should("put the first-declared flavor dimension last (highest priority) among multiple dimensions") {
      // flavorDimensions("tier", "region") — per AGP's documented precedence ("the first dimension
      // having a higher priority than the second, and so on" —
      // https://developer.android.com/build/build-variants#flavor-dimensions), "tier" (declared
      // first) must win over "region" (declared second). ComponentIdentity.productFlavors is
      // ordered by declaration order, i.e. [("tier", "free"), ("region", "us")].
      val identity = FakeComponentIdentity(
        name = "freeUsDebug",
        buildType = "debug",
        productFlavors = listOf("tier" to "free", "region" to "us"),
        flavorName = "freeUs",
      )

      // "free" (tier, first-declared, highest priority) must sit AFTER "us" (region) in this
      // ascending-priority list, since resolveOverriddenDotFiles lets later entries win.
      variantSourceSetNamesInPriorityOrder(identity) shouldBe
        listOf("main", "us", "free", "debug", "freeUsDebug")
    }

    should("override a lower-priority dot file with a higher-priority same-relative-path file") {
      val mainDir = createTempSourceSetDir()
      val flavorDir = createTempSourceSetDir()
      try {
        File(mainDir, "app.dot").writeText("main")
        val flavorDotFile = File(flavorDir, "app.dot").apply { writeText("flavor") }

        resolveOverriddenDotFiles(listOf(listOf(mainDir), listOf(flavorDir))) shouldBe
          listOf(flavorDotFile)
      } finally {
        mainDir.deleteRecursively()
        flavorDir.deleteRecursively()
      }
    }

    should("fall back to the lower-priority file for a relative path with no override") {
      val mainDir = createTempSourceSetDir()
      val flavorDir = createTempSourceSetDir()
      try {
        val mainAppFile = File(mainDir, "app.dot").apply { writeText("main-app") }
        val mainOtherFile = File(mainDir, "other.dot").apply { writeText("main-other") }
        val flavorAppFile = File(flavorDir, "app.dot").apply { writeText("flavor-app") }

        resolveOverriddenDotFiles(listOf(listOf(mainDir), listOf(flavorDir))).toSet() shouldBe
          setOf(flavorAppFile, mainOtherFile)

        // sanity check that the non-overridden file really did fall back to main's content
        mainOtherFile.readText() shouldBe "main-other"
        mainAppFile.readText() shouldBe "main-app"
      } finally {
        mainDir.deleteRecursively()
        flavorDir.deleteRecursively()
      }
    }

    should("resolve the full main < flavor < buildType < variant-specific precedence chain") {
      val mainDir = createTempSourceSetDir()
      val flavorDir = createTempSourceSetDir()
      val buildTypeDir = createTempSourceSetDir()
      val variantDir = createTempSourceSetDir()
      try {
        File(mainDir, "a.dot").writeText("main-a")
        File(mainDir, "b.dot").writeText("main-b")
        File(mainDir, "c.dot").writeText("main-c")
        val flavorB = File(flavorDir, "b.dot").apply { writeText("flavor-b") }
        val flavorD = File(flavorDir, "d.dot").apply { writeText("flavor-d") }
        val buildTypeC = File(buildTypeDir, "c.dot").apply { writeText("buildType-c") }
        val variantA = File(variantDir, "a.dot").apply { writeText("variant-a") }

        val resolved = resolveOverriddenDotFiles(
          listOf(listOf(mainDir), listOf(flavorDir), listOf(buildTypeDir), listOf(variantDir)),
        )

        resolved.toSet() shouldBe setOf(variantA, flavorB, buildTypeC, flavorD)
      } finally {
        mainDir.deleteRecursively()
        flavorDir.deleteRecursively()
        buildTypeDir.deleteRecursively()
        variantDir.deleteRecursively()
      }
    }

    should(
      "end-to-end: resolveOverriddenDotFiles picks the override file at the same relative path, " +
        "and codegen on the resolved file reflects the overriding graph, not the base one",
    ) {
      val mainDir = createTempSourceSetDir()
      val flavorDir = createTempSourceSetDir()
      try {
        // Same relative path ("app.dot") in both source sets — the flavor dir's file must fully
        // replace the main dir's file, exactly like a real "src/main/way/app.dot" vs
        // "src/google/way/app.dot" override pair.
        File(mainDir, "app.dot").writeText(
          File("src/test/resources/flavor-override-base.dot").readText(),
        )
        val overrideFile = File(flavorDir, "app.dot").apply {
          writeText(File("src/test/resources/flavor-override-google.dot").readText())
        }

        val resolved = resolveOverriddenDotFiles(listOf(listOf(mainDir), listOf(flavorDir)))

        // The resolver must pick the higher-priority (flavor) file, not the main one.
        resolved shouldBe listOf(overrideFile)

        val generatedSchema = buildSpecs(
          file = resolved.single(),
          // Must be an ancestor of the resolved file with a matching (here: absolute) path type,
          // or `file.toPath().relativeTo(projectDir.toPath())` in parseSchemaDotFile throws.
          projectDir = resolved.single().parentFile,
          config = CodeGenConfig(
            outputPackageName = "ru.kode.test.app.schema",
            outputSchemaClassName = "DefaultTestNavSchema",
          ),
        ).schemaFileSpec.toString()

        // Proof the resolved (override) file, not the base one, is what actually got codegenned:
        // "countrySelect" only exists in the overriding graph.
        generatedSchema shouldContain "FlavorGoogleAppSchema"
        generatedSchema shouldContain "countrySelect"
        generatedSchema shouldNotContain "FlavorBaseAppSchema"
      } finally {
        mainDir.deleteRecursively()
        flavorDir.deleteRecursively()
      }
    }

    should("apply cleanly to a plain Kotlin/JVM project with no Android plugin involved") {
      // Smoke test only — NOT a regression test for the NoClassDefFoundError bug this change
      // fixes. AGP is `testImplementation` on this module's own test classpath (see
      // build.gradle.kts), so `CommonExtension` resolves fine here regardless of whether the fix
      // is applied; this test can't reproduce the real consumer scenario (AGP absent entirely).
      // The actual fix is verified by call-graph tracing: findNonAndroidMainSources() (reached
      // from the afterAndroid=false path, which runs unconditionally) never references any AGP
      // type, unlike findAndroidMainSources()/setupAndroidPerVariantWayTasksIfFlavored() which
      // are only reachable via the withId("com.android.base") callback.
      runCatching { applyWayPluginToPlainKotlinJvmProject() }.isSuccess shouldBe true
    }

    should("detect Android and register the way task when com.android.library is applied AFTER the way plugin") {
      val project = ProjectBuilder.builder().build()
      project.pluginManager.apply(WayPlugin::class.java)
      project.pluginManager.apply("com.android.library")
      (project.extensions.getByName("android") as com.android.build.api.dsl.LibraryExtension).apply {
        namespace = "ru.kode.way.gradle.test.afterorder"
        compileSdk = 34
      }
      (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

      project.tasks.findByName("generateWayClasses") shouldNotBe null
    }

    should("detect Android and register the way task when com.android.library is applied BEFORE the way plugin") {
      val project = ProjectBuilder.builder().build()
      project.pluginManager.apply("com.android.library")
      (project.extensions.getByName("android") as com.android.build.api.dsl.LibraryExtension).apply {
        namespace = "ru.kode.way.gradle.test.beforeorder"
        compileSdk = 34
      }
      project.pluginManager.apply(WayPlugin::class.java)
      (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

      project.tasks.findByName("generateWayClasses") shouldNotBe null
    }

    should("still register a per-variant way task via the flavored path when a flavor overrides a way dot file") {
      // Real end-to-end check (not just the pure resolveOverriddenDotFiles unit tests above) that
      // setupAndroidPerVariantWayTasksIfFlavored() is still reached now that it's only invoked
      // from the withPlugin("com.android.base")-guarded branch of setupWayTasks. A `google`-flavor
      // "way/app.dot" override is required here: merely declaring productFlavors with no actual
      // override anywhere under way/ must now use the cheap single-task path instead (see the
      // build-type-only-override test below), so this test needs a genuine override to still
      // exercise the per-variant path.
      val projectDir = Files.createTempDirectory("way-flavor-project").toFile()
      try {
        File(projectDir, "src/main/way").mkdirs()
        File(projectDir, "src/main/way/app.dot").writeText("digraph { }")
        File(projectDir, "src/google/way").mkdirs()
        File(projectDir, "src/google/way/app.dot").writeText("digraph { }")

        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.pluginManager.apply(WayPlugin::class.java)
        project.pluginManager.apply("com.android.library")
        (project.extensions.getByName("android") as com.android.build.api.dsl.LibraryExtension).apply {
          namespace = "ru.kode.way.gradle.test.flavored"
          compileSdk = 34
          flavorDimensions += "tier"
          productFlavors {
            // Plain Action<T> SAM here (no Gradle Kotlin DSL receiver-style extension applied,
            // since this is a regular Kotlin test file, not a `.gradle.kts` script), so the
            // created flavor must be taken as an explicit parameter, not via `this`.
            create("google") { flavor ->
              (flavor as com.android.build.api.dsl.ProductFlavor).dimension = "tier"
            }
          }
        }
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        project.tasks.findByName("generateGoogleDebugWayClasses") shouldNotBe null
        // Flavored projects go through the per-variant path only, not the no-flavor union task.
        project.tasks.findByName("generateWayClasses") shouldBe null
      } finally {
        projectDir.deleteRecursively()
      }
    }

    should("keep using the cheap single-task path when productFlavors are declared but nothing overrides way/") {
      // The gate must not be tripped by mere flavor declaration — only by an actual override
      // somewhere under way/. Neither of the two existing no-override tests above declares
      // productFlavors, so they don't exercise this branch of the gate on their own.
      val projectDir = Files.createTempDirectory("way-flavors-no-override-project").toFile()
      try {
        File(projectDir, "src/main/way").mkdirs()
        File(projectDir, "src/main/way/app.dot").writeText("digraph { }")

        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.pluginManager.apply(WayPlugin::class.java)
        project.pluginManager.apply("com.android.library")
        (project.extensions.getByName("android") as com.android.build.api.dsl.LibraryExtension).apply {
          namespace = "ru.kode.way.gradle.test.flavorsnooverride"
          compileSdk = 34
          flavorDimensions += "tier"
          productFlavors {
            create("google") { flavor ->
              (flavor as com.android.build.api.dsl.ProductFlavor).dimension = "tier"
            }
          }
        }
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        project.tasks.findByName("generateWayClasses") shouldNotBe null
        project.tasks.findByName("generateGoogleDebugWayClasses") shouldBe null
      } finally {
        projectDir.deleteRecursively()
      }
    }

    should(
      "activate the per-variant override path for a build-type-only override with zero " +
        "productFlavors declared, and reflect the override content in the debug variant's source",
    ) {
      // Regression test for the critical gap: a project with NO productFlavors but a
      // buildType-specific "way/" override (e.g. src/debug/way/app.dot overriding
      // src/main/way/app.dot) must still take the per-variant override-resolution path — the old
      // gate keyed purely on `productFlavors.isEmpty()` fell through to the flat glob-union path
      // here instead, which has no override precedence logic at all.
      val projectDir = Files.createTempDirectory("way-buildtype-only-override-project").toFile()
      try {
        File(projectDir, "src/main/way").mkdirs()
        File(projectDir, "src/main/way/app.dot").writeText(
          File("src/test/resources/flavor-override-base.dot").readText(),
        )
        File(projectDir, "src/debug/way").mkdirs()
        File(projectDir, "src/debug/way/app.dot").writeText(
          File("src/test/resources/flavor-override-google.dot").readText(),
        )

        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.pluginManager.apply(WayPlugin::class.java)
        project.pluginManager.apply("com.android.library")
        (project.extensions.getByName("android") as com.android.build.api.dsl.LibraryExtension).apply {
          namespace = "ru.kode.way.gradle.test.buildtypeonlyoverride"
          compileSdk = 34
        }
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        // (a) the per-variant path activated even though productFlavors is empty.
        val debugTask = project.tasks.findByName("generateDebugWayClasses")
        debugTask shouldNotBe null
        project.tasks.findByName("generateWayClasses") shouldBe null

        // (b) the debug variant's resolved source reflects the override content, not the base —
        // "countrySelect" only exists in the overriding graph.
        val resolvedFiles = (debugTask as GenerateClassesTask).source.files
        resolvedFiles.size shouldBe 1
        resolvedFiles.single().readText() shouldContain "countrySelect"
      } finally {
        projectDir.deleteRecursively()
      }
    }

    should(
      "activate the per-variant override path for a variant-specific-only override " +
        "(e.g. src/googleDebug/way/), with nothing overridden at the flavor or buildType level",
    ) {
      // Regression test for the critical gap: an override that exists ONLY at the composite
      // (flavor+buildType) source set — "googleDebug" — with no override at "google" or "debug"
      // individually, used to be invisible to the old gate: hasVariantOverrideSourceSet() scanned
      // `androidExtension.sourceSets`, which at `finalizeDsl` time (before `onVariants` fires) only
      // contains single-level names ("main", "google", "debug") and never "googleDebug". That made
      // the per-variant path never activate, so `generateGoogleDebugWayClasses` was never
      // registered and the project silently fell through to the old flat-glob-union
      // `generateWayClasses` task instead — reproducing the exact double-codegen/collision failure
      // mode the per-variant mechanism exists to prevent.
      val projectDir = Files.createTempDirectory("way-variant-specific-only-override-project").toFile()
      try {
        File(projectDir, "src/main/way").mkdirs()
        File(projectDir, "src/main/way/app.dot").writeText(
          File("src/test/resources/flavor-override-base.dot").readText(),
        )
        File(projectDir, "src/googleDebug/way").mkdirs()
        File(projectDir, "src/googleDebug/way/app.dot").writeText(
          File("src/test/resources/flavor-override-google.dot").readText(),
        )
        // Deliberately nothing under src/google/way/ or src/debug/way/ — the override exists ONLY
        // at the composite "googleDebug" source set.

        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.pluginManager.apply(WayPlugin::class.java)
        project.pluginManager.apply("com.android.library")
        (project.extensions.getByName("android") as com.android.build.api.dsl.LibraryExtension).apply {
          namespace = "ru.kode.way.gradle.test.variantspecificonlyoverride"
          compileSdk = 34
          flavorDimensions += "tier"
          productFlavors {
            create("google") { flavor ->
              (flavor as com.android.build.api.dsl.ProductFlavor).dimension = "tier"
            }
          }
        }
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        // (a) the per-variant path activated even though the override is only visible on disk at
        // the composite "googleDebug" source set, never at "google" or "debug" alone.
        val googleDebugTask = project.tasks.findByName("generateGoogleDebugWayClasses")
        googleDebugTask shouldNotBe null
        project.tasks.findByName("generateWayClasses") shouldBe null

        // (b) the googleDebug variant's resolved source reflects the override content, not the
        // base — "countrySelect" only exists in the overriding graph.
        val resolvedFiles = (googleDebugTask as GenerateClassesTask).source.files
        resolvedFiles.size shouldBe 1
        resolvedFiles.single().readText() shouldContain "countrySelect"
      } finally {
        projectDir.deleteRecursively()
      }
    }

    should("throw a clear, actionable error naming the file when a dot file parses to an empty navigation graph") {
      // A syntactically VALID but EMPTY graph body (`digraph { }`, already used elsewhere in this
      // file as the "no-op" fixture) parses successfully to an empty adjacencyList — this is the
      // scenario GenerateClassesTask.generate() must guard against: a flavor/buildType override
      // file that accidentally replaced a real base graph with a blank stub must fail with a clear,
      // actionable message instead of an unrelated low-level crash (previously an
      // IllegalStateException("internal error: no root node in graph") from deep inside codegen).
      val projectDir = Files.createTempDirectory("way-empty-schema-project").toFile()
      try {
        val emptySchemaFile = File(projectDir, "app.dot").apply { writeText("digraph { }") }
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val task = project.tasks.create("generateWayClassesForEmptySchema", GenerateClassesTask::class.java) { t ->
          t.outputDirectory.set(project.layout.buildDirectory.dir("generated/way/code/emptySchema"))
          t.source(emptySchemaFile)
          t.include("**/*.dot")
        }

        val exception = shouldThrow<IllegalStateException> { task.generate() }
        exception.message shouldContain "app.dot"
        exception.message shouldContain "empty navigation graph"
      } finally {
        projectDir.deleteRecursively()
      }
    }

    should("also route a genuinely blank/whitespace-only dot file into the same clear empty-graph error") {
      // Regression test: a genuinely blank/whitespace-only .dot file used to fail much earlier, at
      // the ANTLR grammar level, with an unrelated NullPointerException from the parser itself
      // (DotParser.GraphContext.stmt_list() returns null on immediate EOF) — before ever reaching
      // the "empty navigation graph" guard exercised by the test above. findGraphAttributeValue()
      // now null-safely treats a null stmt_list() as having no statements, so this case reaches the
      // same clear, actionable error instead of an unrelated NPE.
      val projectDir = Files.createTempDirectory("way-blank-schema-project").toFile()
      try {
        val blankSchemaFile = File(projectDir, "app.dot").apply { writeText("   \n\t\n") }
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val task = project.tasks.create("generateWayClassesForBlankSchema", GenerateClassesTask::class.java) { t ->
          t.outputDirectory.set(project.layout.buildDirectory.dir("generated/way/code/blankSchema"))
          t.source(blankSchemaFile)
          t.include("**/*.dot")
        }

        val exception = shouldThrow<IllegalStateException> { task.generate() }
        exception.message shouldContain "app.dot"
        exception.message shouldContain "empty navigation graph"
      } finally {
        projectDir.deleteRecursively()
      }
    }

    should("put the first-declared dimension highest-priority for 3+ flavor dimensions, not just 2") {
      // flavorDimensions("tier", "region", "channel")
      val identity = FakeComponentIdentity(
        name = "freeUsStableDebug",
        buildType = "debug",
        productFlavors = listOf("tier" to "free", "region" to "us", "channel" to "stable"),
        flavorName = "freeUsStable",
      )

      // "free" (tier, first-declared) must still end up last (highest priority) among the flavor
      // entries, even with a third dimension ("channel") in play.
      variantSourceSetNamesInPriorityOrder(identity) shouldBe
        listOf("main", "stable", "us", "free", "debug", "freeUsStableDebug")
    }

    should("detect Android and register the way task for a com.android.dynamic-feature module") {
      // The com.android.base-guarded detection path (WayPlugin.apply()'s
      // project.plugins.withId("com.android.base") { ... }) was previously only exercised against
      // com.android.application/com.android.library in this test file. com.android.dynamic-feature
      // also applies "com.android.base" and should be detected the same way. (com.android.test was
      // considered too, but it requires a `targetProjectPath` pointing at a real base app module
      // and fails project configuration in this ProjectBuilder-based, no-GradleTestKit harness;
      // dynamic-feature applies and evaluates cleanly with just a namespace/compileSdk.)
      val project = ProjectBuilder.builder().build()
      project.pluginManager.apply(WayPlugin::class.java)
      project.pluginManager.apply("com.android.dynamic-feature")
      (project.extensions.getByName("android") as com.android.build.api.dsl.DynamicFeatureExtension).apply {
        namespace = "ru.kode.way.gradle.test.dynamicfeature"
        compileSdk = 34
      }
      (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

      project.tasks.findByName("generateWayClasses") shouldNotBe null
    }
  })
