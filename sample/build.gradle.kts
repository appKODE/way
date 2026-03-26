@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.kotlinMultiplatform.get().pluginId)
  alias(libs.plugins.ksp)
  alias(libs.plugins.way)
}

kotlin {
  jvmToolchain(11)

  jvm {
    mainRun {
      mainClass.set("ru.kode.way.sample.MainKt")
    }
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(project(":way"))
        implementation(libs.bundles.coroutines)
      }
    }
    val commonTest by getting {
    }
    val jvmMain by getting {
    }
    val jvmTest by getting {
    }
  }
}
