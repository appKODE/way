plugins {
  id(libs.plugins.kotlinMultiplatform.get().pluginId)
  id(libs.plugins.dokka.get().pluginId)
  `maven-publish`
}

kotlin {
  targets {
    jvm {
      withJava()
      compilations.all {
        kotlinOptions {
          jvmTarget = "1.8"
          moduleName = "way-library"
        }
      }
    }
//    ios()
//    iosSimulatorArm64()
//    macosArm64()
//    macosX64()
  }
  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(libs.bundles.coroutines)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(libs.bundles.koTestCommon)
        implementation(libs.turbine)
      }
    }
    val jvmMain by getting {
    }
    val jvmTest by getting {
      dependencies {
        implementation(libs.bundles.koTestJvm)
      }
    }
  }
}

tasks.named<Test>("jvmTest") {
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
