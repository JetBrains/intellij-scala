package org.jetbrains.plugins.scala
package testingSupport.test.utest

import com.intellij.execution.configurations.{ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfigurationFactory

class UTestRunConfigurationFactory (override val typez: ConfigurationType)
        extends AbstractTestRunConfigurationFactory(typez) {

  def createTemplateConfiguration(project: Project): RunConfiguration = {
    val configuration = new UTestRunConfiguration(project, this, "")
    configuration
  }
}