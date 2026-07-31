package ru.kode.way.gradle

import com.google.devtools.ksp.gradle.KspAATask
import com.google.devtools.ksp.gradle.KspGradleConfig
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

class KspTaskConfigurerTest :
  ShouldSpec({
    should("configure matching ksp task with generated source roots and dependency") {
      val project = ProjectBuilder.builder().build()
      val generatedDir = project.layout.buildDirectory.dir("generated/way/code/main")
      val generateTaskProvider = project.tasks.register("generateWayClasses", GenerateClassesTask::class.java) { task ->
        task.outputDirectory.set(generatedDir)
      }
      val kspTaskProvider = project.tasks.register("kspDebugKotlin", FakeKspAATask::class.java)

      project.configureKspTask(
        kspTaskName = "kspDebugKotlin",
        taskProvider = generateTaskProvider,
      )

      val kspTask = kspTaskProvider.get()
      val generateTask = generateTaskProvider.get()
      val outputDirectory = generatedDir.get().asFile

      kspTask.taskDependencies.getDependencies(kspTask).contains(generateTask) shouldBe true
      kspTask.kspConfig.sourceRoots.files.contains(outputDirectory) shouldBe true
      kspTask.kspConfig.javaSourceRoots.files.contains(outputDirectory) shouldBe true
      kspTask.kspConfig.commonSourceRoots.files.contains(outputDirectory) shouldBe true
    }

    should("configure matching ksp task registered after configureKspTask call") {
      val project = ProjectBuilder.builder().build()
      val generatedDir = project.layout.buildDirectory.dir("generated/way/code/main")
      val generateTaskProvider = project.tasks.register("generateWayClasses", GenerateClassesTask::class.java) { task ->
        task.outputDirectory.set(generatedDir)
      }

      project.configureKspTask(
        kspTaskName = "kspDebugKotlin",
        taskProvider = generateTaskProvider,
      )

      val kspTaskProvider = project.tasks.register("kspDebugKotlin", FakeKspAATask::class.java)
      val kspTask = kspTaskProvider.get()
      val generateTask = generateTaskProvider.get()
      val outputDirectory = generatedDir.get().asFile

      kspTask.taskDependencies.getDependencies(kspTask).contains(generateTask) shouldBe true
      kspTask.kspConfig.sourceRoots.files.contains(outputDirectory) shouldBe true
    }

    should("not configure non matching ksp tasks") {
      val project = ProjectBuilder.builder().build()
      val generatedDir = project.layout.buildDirectory.dir("generated/way/code/main")
      val generateTaskProvider = project.tasks.register("generateWayClasses", GenerateClassesTask::class.java) { task ->
        task.outputDirectory.set(generatedDir)
      }
      val kspTaskProvider = project.tasks.register("kspReleaseKotlin", FakeKspAATask::class.java)

      project.configureKspTask(
        kspTaskName = "kspDebugKotlin",
        taskProvider = generateTaskProvider,
      )

      val kspTask = kspTaskProvider.get()
      val generateTask = generateTaskProvider.get()
      val outputDirectory = generatedDir.get().asFile

      kspTask.taskDependencies.getDependencies(kspTask).contains(generateTask) shouldBe false
      kspTask.kspConfig.sourceRoots.files.contains(outputDirectory) shouldBe false
      kspTask.kspConfig.javaSourceRoots.files.contains(outputDirectory) shouldBe false
      kspTask.kspConfig.commonSourceRoots.files.contains(outputDirectory) shouldBe false
    }

    should("wire two distinct variants' generate tasks to their own ksp task, never to each other's") {
      val project = ProjectBuilder.builder().build()

      val googleDebugDir = project.layout.buildDirectory.dir("generated/way/code/googleDebug")
      val googleDebugGenerateTask = project.tasks.register(
        "generateGoogleDebugWayClasses",
        GenerateClassesTask::class.java,
      ) { task -> task.outputDirectory.set(googleDebugDir) }
      val googleDebugKspTask = project.tasks.register("kspGoogleDebugKotlin", FakeKspAATask::class.java)

      val otherDebugDir = project.layout.buildDirectory.dir("generated/way/code/otherDebug")
      val otherDebugGenerateTask = project.tasks.register(
        "generateOtherDebugWayClasses",
        GenerateClassesTask::class.java,
      ) { task -> task.outputDirectory.set(otherDebugDir) }
      val otherDebugKspTask = project.tasks.register("kspOtherDebugKotlin", FakeKspAATask::class.java)

      project.configureKspTask(kspTaskName = "kspGoogleDebugKotlin", taskProvider = googleDebugGenerateTask)
      project.configureKspTask(kspTaskName = "kspOtherDebugKotlin", taskProvider = otherDebugGenerateTask)

      val googleDebugOutput = googleDebugDir.get().asFile
      val otherDebugOutput = otherDebugDir.get().asFile

      // Each variant's ksp task only sees its own generate task's output, never the other's.
      googleDebugKspTask.get().kspConfig.sourceRoots.files.contains(googleDebugOutput) shouldBe true
      googleDebugKspTask.get().kspConfig.sourceRoots.files.contains(otherDebugOutput) shouldBe false
      otherDebugKspTask.get().kspConfig.sourceRoots.files.contains(otherDebugOutput) shouldBe true
      otherDebugKspTask.get().kspConfig.sourceRoots.files.contains(googleDebugOutput) shouldBe false

      googleDebugKspTask.get().taskDependencies.getDependencies(googleDebugKspTask.get())
        .contains(otherDebugGenerateTask.get()) shouldBe false
      otherDebugKspTask.get().taskDependencies.getDependencies(otherDebugKspTask.get())
        .contains(googleDebugGenerateTask.get()) shouldBe false
    }
  })

private open class FakeKspAATask
@Inject
constructor(workerExecutor: WorkerExecutor, objects: ObjectFactory) :
  KspAATask(workerExecutor) {
  override val kspClasspath: ConfigurableFileCollection = objects.fileCollection()
  override val kspConfig: KspGradleConfig = objects.newInstance(KspGradleConfig::class.java)
  override val commandLineArgumentProviders: ListProperty<CommandLineArgumentProvider> =
    objects.listProperty(CommandLineArgumentProvider::class.java)
}
