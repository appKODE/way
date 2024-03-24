@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.androidLibrary.get().pluginId)
  id(libs.plugins.kotlinAndroid.get().pluginId)
  alias(libs.plugins.way)
  id("kotlin-kapt")
}

android {
  namespace = "ru.kode.way.sample.compose.category.routing"

  compileSdk = 33

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  kotlinOptions {
    jvmTarget = "11"
  }

  buildFeatures {
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
  }
}

dependencies {
  implementation(project(":way"))
  implementation(project(":way-compose"))
  implementation(project(":sample-compose:categories:ui"))
  implementation(project(":sample-compose:core:routing"))

  implementation(libs.dagger)
  kapt(libs.daggerCompiler)
}
