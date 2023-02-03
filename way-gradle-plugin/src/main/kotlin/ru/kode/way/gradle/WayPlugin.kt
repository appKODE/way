package ru.kode.way.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.DomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
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

    val mainSourceSets = findMainSourceSets()
    val mainTask = project.tasks.register("generateWayClasses", GenerateClassesTask::class.java) { task ->
      configureTask(task, mainSourceSets)
    }
    mainSourceSets.forEach { sourceSet ->
      sourceSet.kotlin.srcDir(mainTask)
    }
    (project.extensions.findByName("android") as BaseExtension?)?.run {
      val variants: DomainObjectSet<out BaseVariant> = when (this) {
        is AppExtension -> applicationVariants
        is LibraryExtension -> libraryVariants
        else -> error("unknown Android plugin $this")
      }
      variants.forEach { variant ->
        variant.addJavaSourceFoldersToModel(mainTask.get().outputDirectory)
      }
    }

    val testSourceSets = findTestSourceSets()
    if (testSourceSets.isNotEmpty()) {
      val testTask = project.tasks.register("generateTestWayClasses", GenerateClassesTask::class.java) { task ->
        configureTask(task, testSourceSets)
      }
      testSourceSets.forEach { sourceSet ->
        sourceSet.kotlin.srcDir(testTask)
      }
    }
  }

  private fun Project.configureTask(task: GenerateClassesTask, sourceSets: List<KotlinSourceSet>) {
    task.group = "way"
    val sourceDirs = sourceSets.flatMap { it.kotlin.srcDirs }
    val waySourceDirs = sourceDirs.map { File(it.parentFile, "way") }
    task.source(waySourceDirs)
    task.include("**/*.dot")
  }

  private fun Project.findMainSourceSets(): List<KotlinSourceSet> {
    project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.run {
      return listOf(sourceSets.getByName("commonMain"))
    }

    (project.extensions.findByName("android") as BaseExtension?)?.run {
      return (project.extensions.findByName("kotlin") as KotlinProjectExtension).sourceSets.toList()
    }

    // TODO Kotlin-only project

    return emptyList()
  }

  private fun Project.findTestSourceSets(): List<KotlinSourceSet> {
    project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.run {
      return listOf(sourceSets.getByName("commonTest"))
    }

    // TODO Android project
    // TODO Kotlin-only project
    return emptyList()
  }
}

internal const val LIBRARY_PACKAGE = "ru.kode.way"
internal const val DEFAULT_SCHEMA_CLASS_NAME = "NavigationSchema"
internal const val DEFAULT_TARGETS_FILE_NAME = "Targets"
