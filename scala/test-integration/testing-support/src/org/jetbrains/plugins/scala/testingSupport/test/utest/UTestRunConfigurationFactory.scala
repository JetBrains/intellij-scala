package org.jetbrains.plugins.scala
package testingSupport.test.utest

import com.intellij.execution.configurations.{ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfigurationFactory

class UTestRunConfigurationFactory(override val typ: ConfigurationType)
  extends AbstractTestRunConfigurationFactory(typ) {

  override def id: String = "utest"

  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new UTestRunConfiguration(project, this, "")
}