package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import org.jetbrains.plugins.scala.config.ScalaFacet

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.05.2009
 */

abstract class AbstractTestRunConfigurationFactory(val typez: ConfigurationType) extends ConfigurationFactory(typez)  {
  override def createConfiguration(name: String, template: RunConfiguration): RunConfiguration = {

    val configuration = (super.createConfiguration(name, template)).asInstanceOf[AbstractTestRunConfiguration]
    ScalaFacet.findModulesIn(template.getProject).headOption.foreach {
      configuration.setModule _
    }
    configuration
  }
}