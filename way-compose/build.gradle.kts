@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.androidLibrary.get().pluginId)
  id(libs.plugins.kotlinAndroid.get().pluginId)
  id(libs.plugins.dokka.get().pluginId)
  `maven-publish`
}

android {
  namespace = "ru.kode.way.compose"
  compileSdk = 33

  defaultConfig {
    minSdk = 21

    aarMetadata {
      minCompileSdk = 21
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions {
    jvmTarget = "1.8"
  }

  buildFeatures {
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
  }

  publishing {
    singleVariant("release") {
      withSourcesJar()
    }
  }
}

publishing {
  publications {
    register<MavenPublication>("release") {
      afterEvaluate {
        from(components["release"])
      }
    }
  }
}

dependencies {
  api(libs.composeUi)
  api(libs.composeAnimation)
  api(libs.composeFoundation)
  implementation(project(":way"))
}
