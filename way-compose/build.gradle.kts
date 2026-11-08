import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.kotlinMultiplatform.get().pluginId)
  id(libs.plugins.androidKmpLibrary.get().pluginId)
  alias(libs.plugins.kotlinCompose)
  alias(libs.plugins.vanniktech.maven.publish)
}

group = providers.gradleProperty("pomGroupId").get()
version = providers.gradleProperty("versionName").get()

kotlin {
  androidLibrary {
    namespace = "ru.kode.way.compose"
    compileSdk = libs.versions.compileSdk.get().toInt()
    minSdk = 21
    // JVM 11 bytecode, but no jvmToolchain(11): Robolectric host tests need the running JDK 17+
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }

    withHostTestBuilder {}.configure {
      isIncludeAndroidResources = true
    }
  }

  iosArm64()
  iosSimulatorArm64()
  iosX64()

  sourceSets {
    commonMain.dependencies {
      // Compose Multiplatform; on Android these resolve to the androidx artifacts below
      api(libs.cmpUi)
      api(libs.cmpAnimation)
      api(libs.cmpFoundation)
      implementation(project(":way"))
    }
    androidMain.dependencies {
      api(libs.composeUi)
      api(libs.composeAnimation)
      api(libs.composeFoundation)
    }
    getByName("androidHostTest").dependencies {
      implementation(libs.composeUiTestJunit4)
      implementation(libs.composeUiTestManifest)
      implementation(libs.robolectric)
      implementation(libs.junit)
    }
  }
}

mavenPublishing {
  coordinates(artifactId = "way-compose")
  configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty()))

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
