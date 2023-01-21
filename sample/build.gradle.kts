@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.kotlinMultiplatform.get().pluginId)
  alias(libs.plugins.way)
  id("application")
}

kotlin {
  targets {
    jvm {
      withJava()
      compilations.configureEach {
        kotlinOptions {
          jvmTarget = "1.8"
        }
      }
    }
  }
  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(project(":way"))
        implementation(libs.bundles.coroutines)
      }
    }
    val commonTest by getting {
    }
    val jvmMain by getting {
    }
    val jvmTest by getting {
    }
  }
}

application {
  mainClassName = "ru.kode.way.sample.MainKt"
}
