package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import com.intellij.execution.configurations.{ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfigurationFactory

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