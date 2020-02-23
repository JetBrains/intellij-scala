package org.jetbrains.plugins.scala
package project.template

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.platform.{ProjectTemplate, ProjectTemplatesFactory}
import javax.swing.Icon
import org.jetbrains.plugins.scala.icons.Icons

/**
  * @author Pavel Fatin
  */
class ScalaProjectTemplatesFactory extends ProjectTemplatesFactory {
  override def getGroups: Array[String] = Array(ScalaProjectTemplatesFactory.Group)

  override def getGroupIcon(group: String): Icon = Icons.SCALA_SMALL_LOGO

  override def createTemplates(group: String, context: WizardContext): Array[ProjectTemplate] = {
    val entity = if (context.isCreatingNewProject) WizardEntity.Project else WizardEntity.Module

    Array(new ScalaProjectTemplate(entity))
  }
}

object ScalaProjectTemplatesFactory {
  val Group = "Scala"
}