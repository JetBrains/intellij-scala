package org.jetbrains.plugins.scala
package project.template

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.platform.{ProjectTemplate, ProjectTemplatesFactory}
import org.jetbrains.plugins.scala.icons.Icons

/**
  * @author Pavel Fatin
  */
class ScalaProjectTemplatesFactory extends ProjectTemplatesFactory {
  def getGroups = Array(ScalaProjectTemplatesFactory.Group)

  override def getGroupIcon(group: String) = Icons.SCALA_SMALL_LOGO

  def createTemplates(group: String, context: WizardContext): Array[ProjectTemplate] = {
    val entity = if (context.isCreatingNewProject) WizardEntity.Project else WizardEntity.Module

    Array(new ScalaProjectTemplate(entity))
  }
}

object ScalaProjectTemplatesFactory {
  val Group = "Scala"
}