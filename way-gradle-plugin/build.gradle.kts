@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.kotlinJvm.get().pluginId)
  `java-gradle-plugin`
  `maven-publish`
  antlr
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
  testImplementation(libs.bundles.koTestJvm)

  antlr(libs.antlr)
}

tasks.withType<JavaCompile> {
  sourceCompatibility = JavaVersion.VERSION_1_8.toString()
  targetCompatibility = JavaVersion.VERSION_1_8.toString()
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}
