package org.jetbrains.plugins.cbt.project.template

import javax.swing.Icon

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.platform.{ProjectTemplate, ProjectTemplatesFactory}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.template.ScalaProjectTemplatesFactory


class CbtIdeaProjectTemplateFactory extends ProjectTemplatesFactory {
  override def getGroups = Array(ScalaProjectTemplatesFactory.Group)

  override def getGroupIcon(group: String): Icon = Icons.SCALA_SMALL_LOGO

  override def createTemplates(group: String, context: WizardContext): Array[ProjectTemplate] =
    if (context.isCreatingNewProject) Array(new CbtProjectTemplate)
    else Array.empty
}


