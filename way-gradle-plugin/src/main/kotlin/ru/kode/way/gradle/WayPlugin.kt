package ru.kode.way.gradle

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal const val MIN_GRADLE_VERSION = "8.0"

class WayPlugin : Plugin<Project> {
  private val android = AtomicBoolean(false)
  private val kotlin = AtomicBoolean(false)

  override fun apply(project: Project) {
    require(GradleVersion.current() >= GradleVersion.version(MIN_GRADLE_VERSION)) {
      "Way requires Gradle version $MIN_GRADLE_VERSION or greater."
    }

    project.plugins.withId("com.android.base") {
      android.set(true)
      project.extensions.getByType(AndroidComponentsExtension::class.java).finalizeDsl {
        project.setupWayTasks(afterAndroid = true)
      }
    }

    project.plugins.withType(KotlinBasePlugin::class.java) {
      kotlin.set(true)
    }

    project.afterEvaluate {
      project.setupWayTasks(afterAndroid = false)
    }
  }

  private fun Project.setupWayTasks(afterAndroid: Boolean) {
    if (android.get() && !afterAndroid) return

    check(kotlin.get()) {
      "Way Gradle plugin applied in " +
        "project '${project.path}' but no supported Kotlin plugin was found"
    }

    // AGP types (CommonExtension / AndroidComponentsExtension) must only ever be resolved when
    // Android is genuinely applied to this project. `afterAndroid == true` is only ever passed
    // from the `project.pluginManager.withPlugin("com.android.base")` callback in `apply()`, so
    // reaching `findAndroidMainSources()` / `setupAndroidPerVariantWayTasksIfFlavored()` here is
    // safe. The `afterAndroid == false` branch (from `project.afterEvaluate`) must stay entirely
    // clear of those types, since it also runs for plain Kotlin/JVM consumers that never apply
    // Android and may not even have AGP on their classpath (`compileOnly` here).
    val mainSources = if (afterAndroid) {
      if (setupAndroidPerVariantWayTasksIfFlavored()) emptyList() else findAndroidMainSources()
    } else {
      findNonAndroidMainSources()
    }
    if (mainSources.isNotEmpty()) {
      val mainTask = project.tasks.register(
        "generateWayClasses",
        GenerateClassesTask::class.java,
      ) { task ->
        task.outputDirectory.set(
          project.layout.buildDirectory.dir("generated/way/code/${mainSources.first().name}"),
        )
        configureTask(task, mainSources)
      }
      mainSources.forEach { source ->
        source.registerGeneratedDir(mainTask)
      }
    }

    val testSources = findTestSources()
    if (testSources.isNotEmpty()) {
      val testTask = project.tasks.register(
        "generateTestWayClasses",
        GenerateClassesTask::class.java,
      ) { task ->
        task.outputDirectory.set(
          project.layout.buildDirectory.dir("generated/way/code/${testSources.first().name}"),
        )
        configureTask(task, testSources)
      }

      testSources.forEach { source ->
        source.registerGeneratedDir(testTask)
      }
    }
  }

  private fun configureTask(task: GenerateClassesTask, sources: List<Source>) {
    task.group = "way"
    task.include("**/*.dot")
    sources.forEach { source ->
      val waySourceDirs = source.sourceDirectories.map { sourceDirectories ->
        sourceDirectories.map { sourceDirectory ->
          sourceDirectory.resolveWaySourceDir(source.name)
        }
      }
      task.source(waySourceDirs)
    }
  }

  /**
   * Main-sources resolution for a project where Android is confirmed applied (only ever called
   * from the `afterAndroid = true` branch of [setupWayTasks], itself only reachable from the
   * `project.pluginManager.withPlugin("com.android.base")` callback in [apply] — so it is safe
   * here to resolve AGP's `CommonExtension`/`AndroidComponentsExtension` types). KMP still takes
   * priority (a KMP module that also targets Android applies "com.android.base" too, but its
   * sources must still be resolved via `commonMain`, not the Android source sets).
   */
  private fun Project.findAndroidMainSources(): List<Source> {
    findKotlinMultiplatformMainSource()?.let { return listOf(it) }

    // Android project
    val androidExtension = project.extensions.findByType(CommonExtension::class.java)
    if (androidExtension != null) {
      // Read resolved source roots from srcDirs; directories snapshots can contain
      // provider placeholders (e.g. provider(?)) in AGP built-in Kotlin setups.
      val sourceDirectories = findAndroidSourceDirectories(
        androidExtension = androidExtension,
        kotlinExtension = project.extensions.findByType(KotlinProjectExtension::class.java),
      )
      if (androidExtension.sourceSets.any { sourceSet -> !isWayTestSourceSet(sourceSet.name) }) {
        val androidComponents = project.extensions.findByType(AndroidComponentsExtension::class.java)
        if (androidComponents != null) {
          return listOf(
            Source(
              name = "main",
              sourceDirectories = sourceDirectories,
              registerGeneratedDir = { taskProvider ->
                // AGP 8.x can expose null `variant.sources.kotlin` for some Android+Kotlin setups.
                // Registering on Kotlin's `main` source set keeps generated Kotlin visible to compile tasks.
                registerGeneratedDirInKotlinMainSourceSet(
                  project.extensions.findByType(KotlinProjectExtension::class.java),
                  taskProvider,
                )

                androidComponents.onVariants(androidComponents.selector().all()) { variant ->
                  variant.sources.kotlin?.addGeneratedSourceDirectory(
                    taskProvider,
                    GenerateClassesTask::outputDirectory,
                  ) ?: variant.sources.java?.addGeneratedSourceDirectory(
                    taskProvider,
                    GenerateClassesTask::outputDirectory,
                  )

                  project.pluginManager.withPlugin("com.google.devtools.ksp") {
                    val kspTaskName = variant.computeTaskName("ksp", "Kotlin")
                    project.configureKspTask(kspTaskName, taskProvider)
                  }
                }
              },
            ),
          )
        }
      }
    }

    findPlainKotlinMainSource()?.let { return listOf(it) }

    return emptyList()
  }

  /**
   * Main-sources resolution for a project where Android is NOT applied (only ever called from the
   * `afterAndroid = false` branch of [setupWayTasks], reached via `project.afterEvaluate`, which
   * runs unconditionally for every consumer — including plain Kotlin/JVM projects that may not
   * have AGP on their classpath at all). Must never reference `CommonExtension` /
   * `AndroidComponentsExtension`, unlike [findAndroidMainSources].
   */
  private fun Project.findNonAndroidMainSources(): List<Source> {
    findKotlinMultiplatformMainSource()?.let { return listOf(it) }
    findPlainKotlinMainSource()?.let { return listOf(it) }
    return emptyList()
  }

  /** Shared by [findAndroidMainSources] and [findNonAndroidMainSources]; touches no AGP types. */
  private fun Project.findKotlinMultiplatformMainSource(): Source? {
    val commonMain = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
      ?.sourceSets
      ?.findByName("commonMain")
      ?: return null
    return Source(
      name = "commonMain",
      sourceDirectories = providers.provider { commonMain.kotlin.srcDirs.toList() },
      registerGeneratedDir = { taskProvider ->
        commonMain.kotlin.srcDir(taskProvider)
      },
    )
  }

  /** Shared by [findAndroidMainSources] and [findNonAndroidMainSources]; touches no AGP types. */
  private fun Project.findPlainKotlinMainSource(): Source? {
    val kotlinExtension = project.extensions.findByName("kotlin") as? KotlinProjectExtension
      ?: return null
    val mainSourceSet = kotlinExtension.sourceSets.findByName("main") ?: return null
    return Source(
      name = "main",
      sourceDirectories = providers.provider { mainSourceSet.kotlin.srcDirs.toList() },
      registerGeneratedDir = { taskProvider ->
        mainSourceSet.kotlin.srcDir(taskProvider)
      },
    )
  }

  private fun Project.findTestSources(): List<Source> {
    project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.run {
      val commonTest = sourceSets.findByName("commonTest")
      if (commonTest != null) {
        return listOf(
          Source(
            name = "commonTest",
            sourceDirectories = providers.provider { commonTest.kotlin.srcDirs.toList() },
            registerGeneratedDir = { taskProvider ->
              commonTest.kotlin.srcDir(taskProvider)
            },
          ),
        )
      }
    }
    return emptyList()
  }

  /**
   * Per-VARIANT (flavor x buildType) DOT file routing for Android projects that have at least one
   * variant-affecting `way/` override — a buildType-, flavor-, or variant-specific-named source
   * set that contributes its own `.dot` file(s) under `way/` on top of (or replacing) `main`. Registers one
   * [GenerateClassesTask] per distinct resolved `.dot` file set (usually one per variant, but
   * variants that resolve to an identical file set — e.g. no overrides at all for any of their
   * constituent source sets — reuse the same task) and wires ONLY the matching variant to it,
   * instead of the single glob-union task + wire-everything approach used for the (far more common)
   * no-override case.
   *
   * Returns `false` (doing nothing) for any project that isn't Android, or that IS Android but has
   * no such override anywhere (e.g. a project with `productFlavors` declared that never actually
   * puts anything under a flavor/buildType `way/` directory) — so the caller can fall back to the
   * pre-existing [findAndroidMainSources] path, which remains completely unchanged and therefore
   * byte-identical for every consumer that doesn't use per-variant overrides. Note this gate is
   * intentionally NOT keyed on `productFlavors.isEmpty()`: a project with zero product flavors but
   * a buildType-only override (e.g. `src/debug/way/app.dot` overriding `src/main/way/app.dot`) must
   * also take the per-variant path, since the flat glob-union path has no override precedence logic.
   */
  private fun Project.setupAndroidPerVariantWayTasksIfFlavored(): Boolean {
    // KMP module targeting Android: leave entirely to the commonMain path in findAndroidMainSources().
    if (project.extensions.findByType(KotlinMultiplatformExtension::class.java)
        ?.sourceSets
        ?.findByName("commonMain") != null
    ) {
      return false
    }
    val androidExtension = project.extensions.findByType(CommonExtension::class.java) ?: return false
    if (androidExtension.sourceSets.none { sourceSet -> !isWayTestSourceSet(sourceSet.name) }) return false
    // Only activate the (more expensive) per-variant path when some non-main, non-test source set
    // genuinely contributes a `way/*.dot` file that could override something. A project that merely
    // declares `productFlavors` but never puts anything under a flavor/buildType `way/` dir should
    // keep using the cheap single-task `findAndroidMainSources()` path.
    //
    // This walks `src/` on disk directly (by the same naming convention `androidWaySourceSetDirectories`
    // uses) instead of querying `androidExtension.sourceSets` for candidate names: at `finalizeDsl`
    // time (when this runs, before `onVariants` fires) that DSL container only contains single-level
    // names ("main", each buildType, each flavor) and does NOT yet contain composite/variant-specific
    // names like "googleDebug" — so a variant-specific-only override would be silently missed by a
    // DSL-container scan. Scanning the filesystem directly finds any such directory regardless of
    // whether AGP's DSL container knows about it yet.
    val overrideDirNames = waySourceSetOverrideDirNames()
    if (overrideDirNames.isEmpty()) return false
    val androidComponents = project.extensions.findByType(AndroidComponentsExtension::class.java) ?: return false

    val tasksByResolvedFileSet = mutableMapOf<List<String>, TaskProvider<GenerateClassesTask>>()
    val consumedSourceSetNames = mutableSetOf<String>()

    androidComponents.onVariants(androidComponents.selector().all()) { variant ->
      val sourceSetNames = variantSourceSetNamesInPriorityOrder(variant)
      consumedSourceSetNames += sourceSetNames
      val sourceSetDirs = sourceSetNames.map { sourceSetName ->
        androidWaySourceSetDirectories(sourceSetName)
      }
      // Eagerly resolve once here (at configuration time) purely to compute the dedup key below —
      // task creation/naming/dedup is inherently a configuration-time decision. The actual file
      // list handed to `task.source(...)` is re-resolved lazily via a Provider so it defers the
      // directory walk to execution time and correctly reacts to filesystem changes, matching the
      // laziness convention used elsewhere in this file (see `configureTask()`).
      val resolvedFiles = resolveOverriddenDotFiles(sourceSetDirs)
      if (resolvedFiles.isEmpty()) return@onVariants

      val resolvedFileSetKey = resolvedFiles.map { it.absolutePath }.sorted()
      val taskProvider = tasksByResolvedFileSet.getOrPut(resolvedFileSetKey) {
        project.tasks.register(
          "generate${variant.name.replaceFirstChar(Char::uppercase)}WayClasses",
          GenerateClassesTask::class.java,
        ) { task ->
          task.group = "way"
          task.outputDirectory.set(
            project.layout.buildDirectory.dir("generated/way/code/${variant.name}"),
          )
          task.source(providers.provider { resolveOverriddenDotFiles(sourceSetDirs) })
        }
      }

      variant.sources.kotlin?.addGeneratedSourceDirectory(
        taskProvider,
        GenerateClassesTask::outputDirectory,
      ) ?: variant.sources.java?.addGeneratedSourceDirectory(
        taskProvider,
        GenerateClassesTask::outputDirectory,
      )

      project.pluginManager.withPlugin("com.google.devtools.ksp") {
        val kspTaskName = variant.computeTaskName("ksp", "Kotlin")
        project.configureKspTask(kspTaskName, taskProvider)
      }
    }

    project.afterEvaluate {
      val unmatchedDirNames = overrideDirNames - consumedSourceSetNames
      if (unmatchedDirNames.isNotEmpty()) {
        logger.warn(
          "way: found way/*.dot file(s) under src/{} but no build variant resolves to " +
            "that source set name — this override will never be applied. Check for a typo " +
            "against your declared productFlavors/buildTypes.",
          unmatchedDirNames.sorted().joinToString(", src/"),
        )
      }
    }

    return true
  }

  /**
   * The `way/` directory contributed by a single named Android source set (e.g. "google", "debug",
   * or a variant-specific composite like "googleDebug"), resolved purely by the `src/<sourceSetName>/`
   * naming convention — not by querying AGP's `CommonExtension.sourceSets` DSL container.
   *
   * Per official AGP guidance (developer.android.com/build/extend-agp), third-party plugins should
   * discover source directories via `AndroidComponentsExtension.onVariants()` + `variant.sources`
   * rather than the DSL. However AGP 9's Variant API only exposes `variant.sources.kotlin`/`.java` as
   * a `SourceDirectories.Flat` — the final MERGED list of directories for a variant, with no
   * per-source-set-tier breakdown (unlike `res`/`assets`/`jniLibs`, which are `SourceDirectories.Layered`
   * and DO expose a tiered `List<Collection<Directory>>`). Since [resolveOverriddenDotFiles] needs the
   * per-tier breakdown (main vs. flavor vs. buildType vs. variant-specific, each as its own directory
   * list), neither the Variant API nor the DSL container (which also doesn't contain composite names
   * pre-`onVariants`, see [waySourceSetOverrideDirNames]) can supply it directly. Resolving by naming
   * convention sidesteps both problems, using `ComponentIdentity`-derived source set names (official
   * Variant API, via [variantSourceSetNamesInPriorityOrder]) with directories derived by convention.
   *
   * This assumes the default source set layout (`src/<name>/...`), same as [resolveWaySourceDir]
   * already does for the non-flavored path; a consumer that relocates a source set's root away from
   * that convention is not supported here.
   */
  private fun Project.androidWaySourceSetDirectories(sourceSetName: String): List<File> =
    listOf(File(project.projectDir, "src/$sourceSetName").resolveWaySourceDir(sourceSetName))

  /**
   * Names of non-main, non-test Android source sets anywhere under `src/` that contribute a `.dot`
   * file under their `way/` directory. Checked directly against the filesystem, by the same
   * `src/<name>/way/` convention [androidWaySourceSetDirectories] uses, rather than via
   * `androidExtension.sourceSets` — that DSL container does not yet contain composite/variant-specific
   * source set names (e.g. "googleDebug") at the point this runs (`finalizeDsl`, before `onVariants`),
   * so a DSL-container scan would silently miss a variant-specific-only override.
   *
   * Names returned here that never end up matching any real variant's
   * [variantSourceSetNamesInPriorityOrder] (e.g. a typo'd flavor/buildType name) are reported via a
   * build warning by the caller — see the `project.afterEvaluate` block in
   * [setupAndroidPerVariantWayTasksIfFlavored].
   */
  private fun Project.waySourceSetOverrideDirNames(): Set<String> {
    val sourceSetDirs = File(project.projectDir, "src").listFiles { file -> file.isDirectory } ?: return emptySet()
    return sourceSetDirs
      .filter { sourceSetDir ->
        val sourceSetName = sourceSetDir.name
        sourceSetName != "main" &&
          !isWayTestSourceSet(sourceSetName) &&
          androidWaySourceSetDirectories(sourceSetName).any { dir ->
            dir.isDirectory && dir.walkTopDown().any { candidate -> candidate.isFile && candidate.extension == "dot" }
          }
      }
      .map { it.name }
      .toSet()
  }

  private fun Project.findAndroidSourceDirectories(
    androidExtension: CommonExtension,
    kotlinExtension: KotlinProjectExtension?,
  ): Provider<List<File>> = providers.provider {
    androidExtension.sourceSets
      .asSequence()
      .filterNot { sourceSet -> isWayTestSourceSet(sourceSet.name) }
      .flatMap { sourceSet ->
        collectAndroidSourceDirectories(
          kotlinSourceDirectories = kotlinExtension
            ?.sourceSets
            ?.findByName(sourceSet.name)
            ?.kotlin
            ?.srcDirs,
          javaSourceDirectories = sourceSet.java.directories.asSequence().map(::file).toList(),
        ).asSequence()
      }
      .distinct()
      .toList()
  }
}

private data class Source(
  val name: String,
  val sourceDirectories: Provider<List<File>>,
  val registerGeneratedDir: (TaskProvider<GenerateClassesTask>) -> Unit = {},
)

internal const val LIBRARY_PACKAGE = "ru.kode.way"
internal const val DEFAULT_SCHEMA_CLASS_NAME = "NavigationSchema"
internal const val DEFAULT_TARGETS_FILE_NAME = "Targets"

internal fun File.resolveWaySourceDir(sourceSetName: String): File = when (name) {
  "kotlin",
  "java",
  -> File(parentFile ?: this, "way")

  "src" -> File(this, "$sourceSetName/way")

  sourceSetName -> File(this, "way")

  else -> File(this, "way")
}

internal fun isWayTestSourceSet(sourceSetName: String): Boolean = sourceSetName.startsWith("test", ignoreCase = true) ||
  sourceSetName.startsWith("androidTest", ignoreCase = true)

internal fun collectAndroidSourceDirectories(
  kotlinSourceDirectories: Collection<File>?,
  javaSourceDirectories: Collection<File>?,
): List<File> = (
  resolveAndroidLanguageSourceDirectories(
    sourceDirectories = kotlinSourceDirectories,
  ) +
    resolveAndroidLanguageSourceDirectories(
      sourceDirectories = javaSourceDirectories,
    )
  ).distinct()

internal fun resolveAndroidLanguageSourceDirectories(sourceDirectories: Collection<File>?): List<File> =
  sourceDirectories
    .orEmpty()
    .distinct()

internal fun registerGeneratedDirInKotlinMainSourceSet(
  kotlinExtension: KotlinProjectExtension?,
  taskProvider: TaskProvider<GenerateClassesTask>,
) {
  kotlinExtension
    ?.sourceSets
    ?.findByName("main")
    ?.kotlin
    ?.srcDir(taskProvider)
}
