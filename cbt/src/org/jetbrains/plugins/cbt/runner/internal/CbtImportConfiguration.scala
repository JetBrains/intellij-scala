package org.jetbrains.plugins.cbt.runner.internal

import com.intellij.execution.Executor
import com.intellij.execution.configurations.{ConfigurationFactory, RunConfiguration, RunConfigurationBase, RunProfileState}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.{CbtCommandLineState, CbtProcessListener}

class CbtImportConfiguration(project: Project,
                             useDirect: Boolean,
                             listener: CbtProcessListener,
                             configurationFactory: ConfigurationFactory)
  extends RunConfigurationBase(project, configurationFactory, "Import CBT project") {
  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = null

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
    new CbtCommandLineState("buildInfoXml", useDirect, project.getBasePath, listener, environment, Seq.empty)
}
