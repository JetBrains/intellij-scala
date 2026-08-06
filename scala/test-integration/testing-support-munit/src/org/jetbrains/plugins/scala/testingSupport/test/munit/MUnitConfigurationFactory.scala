package org.jetbrains.plugins.scala.testingSupport.test.munit

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfigurationFactory

final class MUnitConfigurationFactory(typ: MUnitConfigurationType)
  extends AbstractTestRunConfigurationFactory(typ) {

  override def id: String = "MUnit"

  override def createTemplateConfiguration(project: Project): RunConfiguration = {
    val configuration = new MUnitConfiguration(project, this, name = "")
    configuration
  }
}
