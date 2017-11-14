package org.jetbrains.plugins.cbt.runner.internal

import javax.swing.Icon

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.cbt.CBT

class CbtImportConfigurationType extends ConfigurationType with DumbAware {
  def getIcon: Icon = CBT.Icon

  def getDisplayName: String = "CBT Import"

  def getConfigurationTypeDescription: String = "CBT Import"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array.empty

  def getId: String = "CbtImportConfiguration"
}

object CbtImportConfigurationType {
  def instance = new CbtImportConfigurationType
}
