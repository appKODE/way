package ru.kode.way.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import java.io.File

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
  })
