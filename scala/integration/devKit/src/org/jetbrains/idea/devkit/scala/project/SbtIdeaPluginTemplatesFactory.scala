package org.jetbrains.idea.devkit.scala.project

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.platform.{ProjectTemplate, ProjectTemplatesFactory}
import org.jetbrains.plugins.scala.project.template.ScalaProjectTemplatesFactory

class SbtIdeaPluginTemplatesFactory extends ProjectTemplatesFactory {
  override def getGroups: Array[String] = Array(ScalaProjectTemplatesFactory.Group)

  override def createTemplates(group: String, context: WizardContext): Array[ProjectTemplate] =
    Array(new SbtIdeaPluginProjectTemplate)
}
