package ru.kode.way.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class WayPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.task("hello") {
      it.doLast {
        println("Hello from the WayPlugin")
      }
    }
  }
}
