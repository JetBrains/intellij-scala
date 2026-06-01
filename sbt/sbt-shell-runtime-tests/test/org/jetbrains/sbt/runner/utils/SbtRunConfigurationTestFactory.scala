package org.jetbrains.sbt.runner.utils

import com.intellij.execution.impl.{RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.runner.{SbtConfigurationType, SbtRunConfiguration}

private[runner] object SbtRunConfigurationTestFactory {

  def createNewSbtTaskRunConfiguration(
    project: Project,
    configurationName: String,
    sbtCommands: String,
    useSbtShellInRunConfig: Boolean,
    workingDir: Option[String] = None,
  ): RunnerAndConfigurationSettingsImpl = {
    // This is a synthetic run configuration in a light fixture, not one produced by importing a build.sbt project.
    val factory = new SbtConfigurationType().confFactory
    val settings = RunManagerImpl.getInstanceImpl(project)
      .createConfiguration(configurationName, factory)
      .asInstanceOf[RunnerAndConfigurationSettingsImpl]

    val configuration = settings.getConfiguration.asInstanceOf[SbtRunConfiguration]
    configuration.tasks = sbtCommands
    configuration.commands = sbtCommands
    configuration.useSbtShell = useSbtShellInRunConfig
    workingDir.foreach(configuration.workingDir = _)

    settings
  }
}
