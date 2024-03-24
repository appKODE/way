@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.androidLibrary.get().pluginId)
  id(libs.plugins.kotlinAndroid.get().pluginId)
}

android {
  namespace = "ru.kode.way.sample.compose.main.parallel.ui"

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
  implementation(libs.composeUi)
  implementation(libs.composeRuntime)
  implementation(libs.composeMaterial)
  implementation(project(":sample-compose:core:ui"))
  implementation(project(":way"))
  implementation(project(":way-compose"))
}
