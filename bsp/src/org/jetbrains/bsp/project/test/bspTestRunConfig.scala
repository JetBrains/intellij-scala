package org.jetbrains.bsp.project.test

import com.intellij.execution.Executor
import com.intellij.execution.configurations._
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import javax.swing.{Icon, JComponent}
import org.jetbrains.bsp.Icons



class BspTestRunType extends ConfigurationType {
  override def getDisplayName: String = "BSP test run"

  override def getConfigurationTypeDescription: String = getDisplayName

  override def getIcon: Icon = Icons.BSP

  override def getId: String = "BSP_TEST_RUN_CONFIGURATION"

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array(new BspTestRunFactory(this))
}

class BspTestRunFactory(t: ConfigurationType) extends ConfigurationFactory(t) {
  override def createTemplateConfiguration(project: Project): RunConfiguration = new BspTestRunConfiguration(project, this, "BSP_TEST_RUN")

  override def getName: String = "BspTestRunFactory"
}


class BspTestRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
  extends RunConfigurationBase[String](project, configurationFactory, name) {
  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new SettingsEditor[RunConfiguration]() {
    override def resetEditorFrom(s: RunConfiguration): Unit = {}

    override def applyEditorTo(s: RunConfiguration): Unit = {}

    //TODO create editor
    override def createEditor(): JComponent = new JComponent {}
  }

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = new BspTestRunProfileState(getProject, this, executor)
}