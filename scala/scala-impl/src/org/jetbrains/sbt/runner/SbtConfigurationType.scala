package org.jetbrains.sbt.runner

import javax.swing.Icon

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.sbt.{Sbt, SbtBundle}

/**
 * Configuration running of sbt task.
 */
class SbtConfigurationType extends ConfigurationType with DumbAware {
  val confFactory = new SbtRunConfigurationFactory(this)
  
  def getIcon: Icon = Sbt.Icon

  def getDisplayName: String = SbtBundle("sbt.runner.displayName")

  def getConfigurationTypeDescription: String = SbtBundle("sbt.runner.description")

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "SbtRunConfiguration"
}