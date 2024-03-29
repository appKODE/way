@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.androidLibrary.get().pluginId)
  id(libs.plugins.kotlinAndroid.get().pluginId)
  alias(libs.plugins.way)
  id("kotlin-kapt")
}

android {
  namespace = "ru.kode.way.sample.compose.login.routing"

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
  implementation(project(":sample-compose:login:ui"))
  api(project(":sample-compose:login:domain"))

  implementation(project(":sample-compose:core:routing"))
  api(project(":sample-compose:permissions:routing"))

  implementation(libs.dagger)
  kapt(libs.daggerCompiler)
}
