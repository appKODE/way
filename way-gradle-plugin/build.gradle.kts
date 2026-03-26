import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.gradlePublish)
  `maven-publish`
  antlr
}

val sharedProperties = Properties().apply {
  val sharedFile = rootProject.file("../gradle.properties")
  if (sharedFile.isFile) {
    sharedFile.inputStream().use(::load)
  }
}

group = providers.gradleProperty("pomGroupId")
  .orElse(sharedProperties.getProperty("pomGroupId") ?: "ru.kode")
  .get()

version = providers.gradleProperty("versionName")
  .orElse(providers.gradleProperty("version"))
  .orElse(sharedProperties.getProperty("versionName") ?: "0.0.0-SNAPSHOT")
  .get()

gradlePlugin {
  website = "https://github.com/appKODE/way"
  vcsUrl = "https://github.com/appKODE/way"

  plugins {
    create("way") {
      id = "ru.kode.way"
      group = "ru.kode"
      implementationClass = "ru.kode.way.gradle.WayPlugin"
      displayName = "Code generation gradle plugin for Way navigation library"
      description = "Code generation gradle plugin for Way navigation library"
      tags = listOf("navigation", "codegen", "android", "kmm", "compose", "compose-desktop")
    }
  }
}

dependencies {
  implementation(libs.kotlinPoet)
  compileOnly(libs.kotlin.plugin)
  compileOnly(libs.android.plugin)
  compileOnly(libs.ksp.gradle.plugin)
  testImplementation(libs.bundles.koTestCommon)
  testImplementation(libs.bundles.koTestJvm)
  testImplementation(libs.okio)
  testImplementation(libs.kotlin.plugin)
  testImplementation(libs.ksp.gradle.plugin)
  testImplementation(gradleTestKit())

  antlr(libs.antlr)
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}

kotlin {
  jvmToolchain(11)
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_11)
  }
}

tasks.generateGrammarSource {
  arguments = arguments + "-visitor" + "-long-messages"
}

tasks.withType<KotlinCompile> {
  dependsOn(tasks.generateGrammarSource)
}

tasks.withType<Javadoc>().configureEach {
  // ANTLR generated Java sources don't contain Javadoc and produce noisy warnings.
  exclude {
    it.file.invariantSeparatorsPath.contains("/build/generated-src/antlr/")
  }
}

tasks.withType<Test> {
  javaLauncher.set(
    javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(17))
    },
  )
  useJUnitPlatform()
  testLogging {
    showExceptions = true
    showStandardStreams = true
    events = setOf(
      org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT,
      org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED,
      org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
      org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
      org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
    )
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }

  // Disable line diffing, it's more useful to see diff in IDE
  systemProperty("kotest.assertions.multi-line-diff-size", Int.MAX_VALUE)
}

// There is a bug in the ANTLR plugin,
// where ANTLR adds the folder to java source set without marking itself as the producer.
// https://github.com/gradle/gradle/issues/19555
sourceSets.configureEach {
  val generateGrammarSource = tasks.named(getTaskName("generate", "GrammarSource"))
  java.srcDir(generateGrammarSource.map { files() })
}
