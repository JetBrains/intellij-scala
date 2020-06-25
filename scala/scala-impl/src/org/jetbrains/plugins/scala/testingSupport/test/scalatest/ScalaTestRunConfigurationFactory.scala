package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import com.intellij.execution.configurations.{ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfigurationFactory

class ScalaTestRunConfigurationFactory(override val typ: ConfigurationType)
  extends AbstractTestRunConfigurationFactory(typ) {

  override def id: String = "ScalaTest"

  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new ScalaTestRunConfiguration(project, this, name = "")
}