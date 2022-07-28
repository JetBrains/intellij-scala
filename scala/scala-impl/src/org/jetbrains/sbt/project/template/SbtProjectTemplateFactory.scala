package org.jetbrains.sbt
package project.template

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.scala.project.template.ScalaProjectTemplatesFactoryBase
import org.jetbrains.sbt.project.template.techhub.TechHubProjectTemplate

class SbtProjectTemplateFactory extends ScalaProjectTemplatesFactoryBase {

  override def createTemplates(group: String, context: WizardContext): Array[ProjectTemplate] = {
    if (context.isCreatingNewProject) {
      Array(
        new SbtProjectTemplate,
        new TechHubProjectTemplate
      )
    } else {
      Array.empty
    }
  }
}
