package org.jetbrains.plugins.scala
package project.template

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.platform.ProjectTemplate

class ScalaProjectTemplatesFactory extends ScalaProjectTemplatesFactoryBase {

  override def createTemplates(group: String, context: WizardContext): Array[ProjectTemplate] = {
    val entity = if (context.isCreatingNewProject) WizardEntity.Project else WizardEntity.Module

    Array(new ScalaProjectTemplate(entity))
  }
}

object ScalaProjectTemplatesFactory {
  val Group = "Scala"
}