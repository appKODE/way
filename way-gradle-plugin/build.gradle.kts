@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.kotlinJvm.get().pluginId)
  `java-gradle-plugin`
  `maven-publish`
}

gradlePlugin {
  plugins {
    create("way") {
      id = "ru.kode.way"
      group = "ru.kode"
      implementationClass = "ru.kode.way.gradle.WayPlugin"
    }
  }
}

dependencies {
  implementation(libs.kotlinPoet)
  implementation(libs.kotlin.plugin)
}
