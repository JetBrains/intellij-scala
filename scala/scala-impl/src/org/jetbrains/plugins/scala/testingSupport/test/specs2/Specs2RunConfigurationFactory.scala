package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import com.intellij.execution.configurations.{ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfigurationFactory

class Specs2RunConfigurationFactory(override val typez: ConfigurationType)
  extends AbstractTestRunConfigurationFactory(typez) {

  override def getIdExplicit: String = "Specs2"

  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new Specs2RunConfiguration(project, this, "")
}