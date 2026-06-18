package org.jetbrains.sbt.runner.beforeLaunch.utils

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.{BeforeRunTask, BeforeRunTaskProvider}
import com.intellij.openapi.project.Project
import org.junit.Assert.{assertEquals, assertNotNull}

import java.util

/**
 * Terminology note:<br>
 * "Before run" is the same as "Before launch" in the UI of Run Configurations
 */
private[runner] object CompileStepBeforeRunTestUtil {

  def assertCompileStepBeforeRunProviderIsAvailable(project: Project): Unit = {
    val provider = compileStepBeforeRunProvider(project)
    assertNotNull("The CompileStepBeforeRun provider must be available for this regression test", provider)
  }

  def assertCompileStepBeforeRunTasksSize(
    project: Project,
    configuration: RunConfiguration,
    errorMessage: String,
    expectedSize: Int,
  ): Unit = {
    val actualTasks = compileStepBeforeRunTasks(project, configuration)
    val actualSize = actualTasks.size()
    assertEquals(errorMessage, expectedSize, actualSize)
  }

  def addCompileStepBeforeRunTask(project: Project, configuration: RunConfiguration): Unit = {
    val compileBeforeRunTask = compileStepBeforeRunProvider(project).createTask(configuration)
    assertNotNull("The CompileStepBeforeRun provider must create a task for SBT run configurations", compileBeforeRunTask)

    addBeforeRunTask(project, configuration, compileBeforeRunTask)

    assertCompileStepBeforeRunTasksSize(project, configuration, "Explicit `Build` before launch task must be added", 1)
  }

  private def addBeforeRunTask(project: Project, configuration: RunConfiguration, compileBeforeRunTask: CompileStepBeforeRun.MakeBeforeRunTask): Unit = {
    val runManager = RunManagerImpl.getInstanceImpl(project)
    val beforeRunTasks = new util.ArrayList[BeforeRunTask[?]](runManager.getBeforeRunTasks(configuration))
    beforeRunTasks.add(compileBeforeRunTask)
    runManager.setBeforeRunTasks(configuration, beforeRunTasks)
  }

  private def compileStepBeforeRunProvider(project: Project): BeforeRunTaskProvider[CompileStepBeforeRun.MakeBeforeRunTask] =
    BeforeRunTaskProvider.getProvider(project, CompileStepBeforeRun.ID)

  private def compileStepBeforeRunTasks(
    project: Project,
    configuration: RunConfiguration
  ): java.util.List[CompileStepBeforeRun.MakeBeforeRunTask] = {
    val runManager = RunManagerImpl.getInstanceImpl(project)
    runManager.getBeforeRunTasks(configuration, CompileStepBeforeRun.ID)
  }
}
