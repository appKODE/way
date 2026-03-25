@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  alias(libs.plugins.spotless)
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinCompose) apply false
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.kotlinJvm) apply false
  alias(libs.plugins.vanniktech.maven.publish) apply false
}

spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("**/build/**/*.*")
    targetExclude("**/resources/*.kt")
    ktlint(libs.versions.ktlint.get()).editorConfigOverride(
      mapOf(
        "indent_size" to "2",
        "max_line_length" to "120",
        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
      ),
    )
    trimTrailingWhitespace()
    endWithNewline()
  }

  kotlinGradle {
    target("**/*.gradle.kts")
    ktlint(libs.versions.ktlint.get()).editorConfigOverride(mapOf("indent_size" to "2", "max_line_length" to "120"))
    trimTrailingWhitespace()
    endWithNewline()
  }
}

tasks.register("setupPluginUploadFromEnvironment") {
  doLast {
    val key = System.getenv("GRADLE_PUBLISH_KEY")
    val secret = System.getenv("GRADLE_PUBLISH_SECRET")

    if (key == null || secret == null) {
      throw GradleException("gradlePublishKey and/or gradlePublishSecret are not defined environment variables")
    }

    System.setProperty("gradle.publish.key", key)
    System.setProperty("gradle.publish.secret", secret)
  }
}
