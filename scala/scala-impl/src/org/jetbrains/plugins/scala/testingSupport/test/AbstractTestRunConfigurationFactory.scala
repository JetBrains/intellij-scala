package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}

abstract class AbstractTestRunConfigurationFactory(val typez: ConfigurationType)
  extends ConfigurationFactory(typez)  {

  override final def getId: String = getIdExplicit
  def getIdExplicit: String

  override def createConfiguration(name: String, template: RunConfiguration): RunConfiguration =
    super.createConfiguration(name, template).asInstanceOf[AbstractTestRunConfiguration]
}