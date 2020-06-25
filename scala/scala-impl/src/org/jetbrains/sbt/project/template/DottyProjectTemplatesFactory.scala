package org.jetbrains.sbt.project.template

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.platform.{ProjectTemplate, ProjectTemplatesFactory}
import org.jetbrains.plugins.scala.project.template.ScalaProjectTemplatesFactory
import org.jetbrains.sbt.SbtBundle

class DottyProjectTemplatesFactory extends ProjectTemplatesFactory {
  override def getGroups: Array[String] = Array(ScalaProjectTemplatesFactory.Group)

  override def createTemplates(group: String, context: WizardContext): Array[ProjectTemplate] = {
    val url = getClass.getClassLoader.getResource("projectTemplates/dottyTemplate.zip")
    val templateName = SbtBundle.message("project.template.name.dotty.experimental")
    val description = SbtBundle.message("project.template.description.dotty")

    //todo: try to download latest version of https://github.com/lampepfl/dotty.g8
    Array(ArchivedSbtProjectTemplate(templateName, description, url))
  }
}
