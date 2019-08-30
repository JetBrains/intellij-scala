package org.jetbrains.sbt
package project.template

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.{ProjectTemplate, ProjectTemplatesFactory}
import javax.swing.Icon
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.template.ScalaProjectTemplatesFactory
import org.jetbrains.sbt.project.template.techhub.TechHubProjectTemplate

/**
  * User: Dmitry.Naydanov, Pavel Fatin
  * Date: 11.03.14.
  */
class SbtProjectTemplateFactory extends ProjectTemplatesFactory {
  override def getGroups = Array(ScalaProjectTemplatesFactory.Group)

  override def getGroupIcon(group: String): Icon = Icons.SCALA_SMALL_LOGO

  override def createTemplates(group: String, context: WizardContext): Array[ProjectTemplate] = {
    if (context.isCreatingNewProject) {

      val defaultTemplates = Array(
        new SbtProjectTemplate,
        new TechHubProjectTemplate
      )
      defaultTemplates ++ dottyTemplates
    } else {
      Array.empty
    }
  }

  private def dottyTemplates: Array[ProjectTemplate] = {
    val url = getClass.getClassLoader.getResource("projectTemplates/dottyTemplate.zip")
    if (ApplicationManager.getApplication.isInternal)
      Array(ArchivedSbtProjectTemplate("Dotty", "Minimal Dotty project", url))
    else Array.empty
  }
}
