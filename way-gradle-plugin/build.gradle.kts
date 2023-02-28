import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.kotlinJvm.get().pluginId)
  alias(libs.plugins.gradlePublish)
  `maven-publish`
  antlr
}

gradlePlugin {
  plugins {
    create("way") {
      id = "ru.kode.way"
      group = "ru.kode"
      implementationClass = "ru.kode.way.gradle.WayPlugin"
      displayName = "Code generation gradle plugin for Way navigation library"
    }
  }
}

pluginBundle {
  website = "https://github.com/appKODE/way"
  vcsUrl = "https://github.com/appKODE/way"
  description = "Code generation gradle plugin for Way navigation library"
  tags = listOf("navigation", "codegen", "android", "kmm", "compose", "compose-desktop")
}

dependencies {
  implementation(libs.kotlinPoet)
  implementation(libs.kotlin.plugin)
  compileOnly(libs.android.plugin)
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

  // Disable line diffing, it's more useful to see diff in IDE
  systemProperty("kotest.assertions.multi-line-diff-size", Int.MAX_VALUE)
}
