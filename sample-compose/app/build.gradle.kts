import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.kotlinCompose)
  alias(libs.plugins.ksp)
}

android {
  namespace = "ru.kode.way.sample.compose"

  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    minSdk = 26
    targetSdk = libs.versions.targetSdk.get().toInt()
    vectorDrawables {
      useSupportLibrary = true
    }
  }

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
  implementation(libs.composeUi)
  implementation(libs.composeRuntime)
  implementation(libs.composeMaterial)
  implementation(libs.activityCompose)
  implementation(project(":way"))
  implementation(project(":way-compose"))
  implementation(project(":sample-compose:app:routing"))
  implementation(project(":sample-compose:core:routing"))

  implementation(libs.dagger)
  ksp(libs.daggerCompiler)
}
