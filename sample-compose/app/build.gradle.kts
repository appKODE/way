@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.androidApplication.get().pluginId)
  id(libs.plugins.kotlinAndroid.get().pluginId)
  id("kotlin-kapt")
}

android {
  namespace = "ru.kode.way.sample.compose"
  compileSdk = 33

  defaultConfig {
    minSdk = 26
    targetSdk = 33
    vectorDrawables {
      useSupportLibrary = true
    }
  }

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
  implementation(libs.composeUi)
  implementation(libs.composeRuntime)
  implementation(libs.composeMaterial)
  implementation(libs.activityCompose)
  implementation(project(":way"))
  implementation(project(":way-compose"))
  implementation(project(":sample-compose:app:routing"))
  implementation(project(":sample-compose:core:routing"))

  implementation(libs.dagger)
  kapt(libs.daggerCompiler)
}
