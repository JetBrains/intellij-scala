package org.jetbrains.sbt.runner

import javax.swing.Icon

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import org.jetbrains.sbt.Sbt

/**
 * Configuration running of sbt task.
 */
class SbtConfigurationType extends ConfigurationType {
  val confFactory = new SbtRunConfigurationFactory(this)
  
  def getIcon: Icon = Sbt.Icon

  def getDisplayName: String = "SBT"

  def getConfigurationTypeDescription: String = "Run SBT task"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "SbtRunConfiguration"
}