package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import com.intellij.execution.configurations.{ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfigurationFactory

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