package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import com.intellij.openapi.project.Project
import com.intellij.execution.configurations.{RunConfiguration, ConfigurationType}
import testingSupport.test.AbstractTestRunConfigurationFactory

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.05.2009
 */

class Specs2RunConfigurationFactory(override val typez: ConfigurationType)
  extends AbstractTestRunConfigurationFactory(typez) {

  def createTemplateConfiguration(project: Project): RunConfiguration = {
    val configuration = new Specs2RunConfiguration(project, this, "")
    configuration
  }
}