@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.kotlinMultiplatform.get().pluginId)
  alias(libs.plugins.way)
  id("application")
}

kotlin {
  jvmToolchain(11)

  targets {
    jvm {
      withJava()
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
  getMainClass().set("ru.kode.way.sample.MainKt")
}
