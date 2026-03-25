import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.androidLibrary.get().pluginId)
  alias(libs.plugins.kotlinCompose)
  alias(libs.plugins.way)
  alias(libs.plugins.ksp)
}

android {
  namespace = "ru.kode.way.sample.compose.main.parallel.routing"

  compileSdk = libs.versions.compileSdk.get().toInt()

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_11)
  }
}

dependencies {
  implementation(project(":way"))
  implementation(project(":way-compose"))
  implementation(project(":sample-compose:main-parallel:ui"))
  implementation(project(":sample-compose:categories:routing"))
  implementation(project(":sample-compose:core:routing"))

  implementation(libs.dagger)
  ksp(libs.daggerCompiler)
}
