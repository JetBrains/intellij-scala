package org.jetbrains.idea.devkit.scala.project

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.scala.project.template.ScalaProjectTemplatesFactoryBase

class SbtIdeaPluginTemplatesFactory extends ScalaProjectTemplatesFactoryBase {

  override def createTemplates(group: String, context: WizardContext): Array[ProjectTemplate] =
    Array(new SbtIdeaPluginProjectTemplate)
}
