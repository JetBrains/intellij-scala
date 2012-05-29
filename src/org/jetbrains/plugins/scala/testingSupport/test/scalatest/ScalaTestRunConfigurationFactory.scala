package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import com.intellij.openapi.project.Project
import com.intellij.execution.configurations.{RunConfiguration, ConfigurationType}
import testingSupport.test.AbstractTestRunConfigurationFactory

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaTestRunConfigurationFactory(override val typez: ConfigurationType)
  extends AbstractTestRunConfigurationFactory(typez) {

  def createTemplateConfiguration(project: Project): RunConfiguration = {
    val configuration = new ScalaTestRunConfiguration(project, this, "")
    configuration
  }
}