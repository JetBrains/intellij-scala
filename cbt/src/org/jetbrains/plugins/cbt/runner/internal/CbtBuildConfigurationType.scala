package org.jetbrains.plugins.cbt.runner.internal

import javax.swing.Icon

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.cbt.CBT

class CbtBuildConfigurationType extends ConfigurationType with DumbAware {
  def getIcon: Icon = CBT.Icon

  def getDisplayName: String = "CBT Internal Task"

  def getConfigurationTypeDescription: String = "CBT Internal Task"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array.empty

  def getId: String = "CbtBuildConfiguration"
}

object CbtBuildConfigurationType {
  def getInstance = new CbtBuildConfigurationType
}


