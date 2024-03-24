@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.androidLibrary.get().pluginId)
  id(libs.plugins.kotlinAndroid.get().pluginId)
  id("kotlin-kapt")
  alias(libs.plugins.way)
}

android {
  namespace = "ru.kode.way.sample.compose.app.routing"

  compileSdk = 33

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  kotlinOptions {
    jvmTarget = "11"
  }
}

dependencies {
  implementation(project(":way"))
  api(project(":sample-compose:login:routing"))
  api(project(":sample-compose:main:routing"))
  api(project(":sample-compose:main-parallel:routing"))

  implementation(libs.dagger)
  kapt(libs.daggerCompiler)
}
