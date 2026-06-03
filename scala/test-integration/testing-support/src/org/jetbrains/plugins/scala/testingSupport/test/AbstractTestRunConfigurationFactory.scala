package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}

abstract class AbstractTestRunConfigurationFactory(val typ: ConfigurationType)
  extends ConfigurationFactory(typ)  {

  def id: String
  override final def getId: String = id
}