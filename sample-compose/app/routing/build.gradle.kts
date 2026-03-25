import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.androidLibrary.get().pluginId)
  alias(libs.plugins.ksp)
  alias(libs.plugins.way)
}

android {
  namespace = "ru.kode.way.sample.compose.app.routing"

  compileSdk = libs.versions.compileSdk.get().toInt()

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_11)
  }
}

dependencies {
  implementation(project(":way"))
  api(project(":sample-compose:login:routing"))
  api(project(":sample-compose:main:routing"))
  api(project(":sample-compose:main-parallel:routing"))

  implementation(libs.dagger)
  ksp(libs.daggerCompiler)
}
