package ru.kode.way.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.DomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class WayPlugin : Plugin<Project> {
  private val android = AtomicBoolean(false)
  private val kotlin = AtomicBoolean(false)

  override fun apply(project: Project) {
    val androidPluginHandler = { _: Plugin<*> ->
      android.set(true)
      project.afterEvaluate {
        project.setupWayTasks(afterAndroid = true)
      }
    }
    project.plugins.withId("com.android.application", androidPluginHandler)
    project.plugins.withId("com.android.library", androidPluginHandler)
    project.plugins.withId("com.android.instantapp", androidPluginHandler)
    project.plugins.withId("com.android.feature", androidPluginHandler)
    project.plugins.withId("com.android.dynamic-feature", androidPluginHandler)

    val kotlinPluginHandler = { _: Plugin<*> -> kotlin.set(true) }
    project.plugins.withId("org.jetbrains.kotlin.multiplatform", kotlinPluginHandler)
    project.plugins.withId("org.jetbrains.kotlin.android", kotlinPluginHandler)
    project.plugins.withId("org.jetbrains.kotlin.jvm", kotlinPluginHandler)

    project.afterEvaluate {
      project.setupWayTasks(afterAndroid = false)
    }
  }

  private fun Project.setupWayTasks(afterAndroid: Boolean) {
    if (android.get() && !afterAndroid) return

    check(kotlin.get()) {
      "Way Gradle plugin applied in " +
        "project \"${project.path}\" but no supported Kotlin plugin was found"
    }

    // TODO add "way" dependencies to project (see sqldelight-gradle-plugin for an example)

    val mainSources = findMainSources()
    val mainTask = project.tasks.register("generateWayClasses", GenerateClassesTask::class.java) { task ->
      configureTask(task, mainSources)
    }
    mainSources.forEach { source ->
      source.sourceDirectorySet.kotlin.srcDir(mainTask)
      source.registerGeneratedDir?.invoke(mainTask)
    }

    val testSources = findTestSources()
    if (testSources.isNotEmpty()) {
      val testTask = project.tasks.register("generateTestWayClasses", GenerateClassesTask::class.java) { task ->
        configureTask(task, testSources)
      }
      testSources.forEach { source ->
        source.sourceDirectorySet.kotlin.srcDir(testTask)
      }
    }
  }

  private fun configureTask(task: GenerateClassesTask, sources: List<Source>) {
    task.group = "way"
    sources.forEach { source ->
      val sourceDirs = source.sourceSets.flatMap { it.kotlin.srcDirs }
      val waySourceDirs = sourceDirs.map { File(it.parentFile, "way") }
      task.source(waySourceDirs)
      task.include("**/*.dot")
    }
  }

  private fun Project.findMainSources(): List<Source> {
    project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.run {
      return listOf(
        Source(
          sourceSets = listOf(sourceSets.getByName("commonMain")),
          sourceDirectorySet = sourceSets.getByName("commonMain")
        )
      )
    }

    (project.extensions.findByName("android") as BaseExtension?)?.run {
      val variants: DomainObjectSet<out BaseVariant> = when (this) {
        is AppExtension -> applicationVariants
        is LibraryExtension -> libraryVariants
        else -> error("unexpected android plugin extension $this")
      }
      val sourceSets = (project.extensions.findByName("kotlin") as KotlinProjectExtension).sourceSets
        .associateBy { it.name }
      return variants.map { variant ->
        Source(
          sourceSets = variant.sourceSets.mapNotNull { variantSourceSet -> sourceSets[variantSourceSet.name] },
          sourceDirectorySet = sourceSets[variant.name]
            ?: error("failed to find sourceSet for variant ${variant.name}"),
          registerGeneratedDir = { taskProvider ->
            variant.addJavaSourceFoldersToModel(taskProvider.get().outputDirectory)
          }
        )
      }
    }

    // TODO Kotlin-only project

    return emptyList()
  }

  private fun Project.findTestSources(): List<Source> {
    project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.run {
      return listOf(
        Source(
          sourceSets = listOf(sourceSets.getByName("commonTest")),
          sourceDirectorySet = sourceSets.getByName("commonTest")
        )
      )
    }

    // TODO Android project
    // TODO Kotlin-only project
    return emptyList()
  }
}

private data class Source(
  val sourceSets: List<KotlinSourceSet>,
  val sourceDirectorySet: KotlinSourceSet,
  val registerGeneratedDir: ((TaskProvider<GenerateClassesTask>) -> Unit)? = null
)

internal const val LIBRARY_PACKAGE = "ru.kode.way"
internal const val DEFAULT_SCHEMA_CLASS_NAME = "NavigationSchema"
internal const val DEFAULT_TARGETS_FILE_NAME = "Targets"
