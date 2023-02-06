pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "way"

include(":way")
include(":way-compose")
include(":way-gradle-plugin")
include(":sample")
include(":sample-compose:core:ui")
include(":sample-compose:app")
include(":sample-compose:app:routing")
include(":sample-compose:permissions:routing")
include(":sample-compose:permissions:ui")
include(":sample-compose:permissions:domain")
