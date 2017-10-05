package org.jetbrains.sbt.runner

import javax.swing.Icon

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import org.jetbrains.sbt.{SbtBundle, Sbt}

/**
 * Configuration running of sbt task.
 */
class SbtConfigurationType extends ConfigurationType {
  val confFactory = new SbtRunConfigurationFactory(this)
  
  def getIcon: Icon = Sbt.Icon

  def getDisplayName: String = SbtBundle("sbt.runner.displayName")

  def getConfigurationTypeDescription: String = SbtBundle("sbt.runner.description")

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "SbtRunConfiguration"
}