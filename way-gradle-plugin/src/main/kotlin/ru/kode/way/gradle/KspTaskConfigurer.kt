package ru.kode.way.gradle

import com.google.devtools.ksp.gradle.KspAATask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

internal fun Project.configureKspTask(kspTaskName: String, taskProvider: TaskProvider<GenerateClassesTask>) {
  tasks
    .withType(KspAATask::class.java)
    .matching { it.name == kspTaskName }
    .configureEach { task ->
      task.dependsOn(taskProvider)
      val generatedDirectory = taskProvider.flatMap { it.outputDirectory }
      task.kspConfig.javaSourceRoots.from(generatedDirectory)
      task.kspConfig.sourceRoots.from(generatedDirectory)
      task.kspConfig.commonSourceRoots.from(generatedDirectory)
    }
}
