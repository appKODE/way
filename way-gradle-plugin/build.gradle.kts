import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
  testImplementation(libs.bundles.koTestCommon)
  testImplementation(libs.bundles.koTestJvm)
  testImplementation(libs.okio)

  antlr(libs.antlr)
}

tasks.generateGrammarSource {
  arguments = arguments + "-visitor" + "-long-messages"
}

tasks.withType<KotlinCompile> {
  dependsOn(tasks.generateGrammarSource)
}

tasks.withType<JavaCompile> {
  sourceCompatibility = JavaVersion.VERSION_1_8.toString()
  targetCompatibility = JavaVersion.VERSION_1_8.toString()
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    showExceptions = true
    showStandardStreams = true
    events = setOf(
      org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT,
      org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED,
      org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
      org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
      org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
    )
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
