@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.kotlinMultiplatform.get().pluginId)
  id(libs.plugins.androidKmpLibrary.get().pluginId)
  // KSP before Kotest: the Kotest plugin needs it to run specs on native
  id(libs.plugins.ksp.get().pluginId)
  id(libs.plugins.kotest.get().pluginId)
  alias(libs.plugins.way)
  alias(libs.plugins.vanniktech.maven.publish)
}

group = providers.gradleProperty("pomGroupId").get()
version = providers.gradleProperty("versionName").get()

kotlin {
  jvmToolchain(11)

  jvm {
    compilerOptions {
      moduleName.set("way-library")
    }
  }

  androidLibrary {
    namespace = "ru.kode.way"
    compileSdk = libs.versions.compileSdk.get().toInt()
    minSdk = 21
  }

  iosArm64()
  iosSimulatorArm64()
  iosX64()

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
    val jvmTest by getting {
      dependencies {
        implementation(libs.bundles.koTestJvm)
      }
    }
  }
}

mavenPublishing {
  coordinates(artifactId = "way")

  publishToMavenCentral()
  signAllPublications()

  pom {
    val pomName: String by project
    val pomDescription: String by project
    val pomUrl: String by project
    val pomScmUrl: String by project
    val pomScmConnection: String by project
    val pomScmDevConnection: String by project
    val pomLicenseName: String by project
    val pomLicenseUrl: String by project
    val pomLicenseDist: String by project
    val pomDeveloperId: String by project
    val pomDeveloperName: String by project

    name.set(pomName)
    description.set(pomDescription)
    url.set(pomUrl)

    scm {
      url.set(pomScmUrl)
      connection.set(pomScmConnection)
      developerConnection.set(pomScmDevConnection)
    }

    licenses {
      license {
        name.set(pomLicenseName)
        url.set(pomLicenseUrl)
        distribution.set(pomLicenseDist)
      }
    }

    developers {
      developer {
        id.set(pomDeveloperId)
        name.set(pomDeveloperName)
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
      org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
    )
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
