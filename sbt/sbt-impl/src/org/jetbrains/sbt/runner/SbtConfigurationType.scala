package org.jetbrains.sbt.runner

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.sbt.{Sbt, SbtBundle}

import javax.swing.Icon

/**
 * Configuration running of sbt task.
 */
class SbtConfigurationType extends ConfigurationType with DumbAware {
  val confFactory = new SbtRunConfigurationFactory(this)
  
  override def getIcon: Icon = Sbt.Icon

  override def getDisplayName: String = SbtBundle.message("sbt.runner.displayName")

  override def getConfigurationTypeDescription: String = SbtBundle.message("sbt.runner.description")

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  override def getId: String = "SbtRunConfiguration"
}