package ru.kode.way.gradle

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
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

    val mainSources = findMainSources()
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
    sources.forEach { source ->
      val waySourceDirs = source.sourceDirectories.map { sourceDirectory ->
        sourceDirectory.resolveWaySourceDir(source.name)
      }
      task.source(waySourceDirs)
      task.include("**/*.dot")
    }
  }

  private fun Project.findMainSources(): List<Source> {
    // Multiplatform project
    project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.run {
      val commonMain = sourceSets.findByName("commonMain")
      if (commonMain != null) {
        return listOf(
          Source(
            name = "commonMain",
            sourceDirectories = commonMain.kotlin.srcDirs.toList(),
            registerGeneratedDir = { taskProvider ->
              commonMain.kotlin.srcDir(taskProvider)
            },
          ),
        )
      }
    }

    // Android project
    val androidExtension = project.extensions.findByType(CommonExtension::class.java)
    if (androidExtension != null) {
      // AGP sourceSet.kotlin.directories API changed semantics across versions.
      // Kotlin main source set srcDirs is stable and points to src/main/kotlin|java.
      val sourceDirectories = findAndroidSourceDirectories(androidExtension)
      if (sourceDirectories.isNotEmpty()) {
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

    // Kotlin project
    (project.extensions.findByName("kotlin") as? KotlinProjectExtension)?.run {
      val mainSourceSet = sourceSets.findByName("main")
      if (mainSourceSet != null) {
        return listOf(
          Source(
            name = "main",
            sourceDirectories = mainSourceSet.kotlin.srcDirs.toList(),
            registerGeneratedDir = { taskProvider ->
              mainSourceSet.kotlin.srcDir(taskProvider)
            },
          ),
        )
      }
    }

    return emptyList()
  }

  private fun Project.findTestSources(): List<Source> {
    project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.run {
      val commonTest = sourceSets.findByName("commonTest")
      if (commonTest != null) {
        return listOf(
          Source(
            name = "commonTest",
            sourceDirectories = commonTest.kotlin.srcDirs.toList(),
            registerGeneratedDir = { taskProvider ->
              commonTest.kotlin.srcDir(taskProvider)
            },
          ),
        )
      }
    }
    return emptyList()
  }

  private fun Project.findAndroidSourceDirectories(androidExtension: CommonExtension): List<File> =
    androidExtension.sourceSets
      .asSequence()
      .filterNot { sourceSet -> isWayTestSourceSet(sourceSet.name) }
      .flatMap { sourceSet ->
        sequenceOf(sourceSet.kotlin, sourceSet.java)
          .flatMap { sourceDirectorySet -> sourceDirectorySet.directories.asSequence() }
      }
      .map(::file)
      .distinct()
      .toList()
}

private data class Source(
  val name: String,
  val sourceDirectories: List<File>,
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
