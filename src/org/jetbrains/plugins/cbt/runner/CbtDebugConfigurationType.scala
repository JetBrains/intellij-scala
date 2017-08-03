package org.jetbrains.plugins.cbt.runner

import javax.swing.Icon

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import org.jetbrains.plugins.cbt.CBT

class CbtDebugConfigurationType extends ConfigurationType {
  val confFactory = new CbtDebugConfigurationFactory(this)

  def getIcon: Icon = CBT.Icon

  def getDisplayName: String = "CBT Debug"

  def getConfigurationTypeDescription: String = "CBT Debug"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "CbtRunDebugConfiguration"
}

object CbtDebugConfigurationType {
  def getInstance = new CbtDebugConfigurationType
}


