@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.androidLibrary.get().pluginId)
  id(libs.plugins.kotlinAndroid.get().pluginId)
  alias(libs.plugins.way)
  id("kotlin-kapt")
}

android {
  compileSdk = 33

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions {
    jvmTarget = "1.8"
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
  implementation(project(":sample-compose:permissions:routing"))

  implementation(libs.dagger)
  kapt(libs.daggerCompiler)
}
