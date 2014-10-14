package org.jetbrains.sbt
package project.template

import com.intellij.platform.ProjectTemplatesFactory
import com.intellij.ide.util.projectWizard.WizardContext
import org.jetbrains.plugins.scala.project.template.ScalaProjectTemplatesFactory

/**
 * User: Dmitry.Naydanov, Pavel Fatin
 * Date: 11.03.14.
 */
class SbtProjectTemplateFactory extends ProjectTemplatesFactory {
  override def getGroups = Array(ScalaProjectTemplatesFactory.Group)

  override def createTemplates(group: String, context: WizardContext) =
    if (context.isCreatingNewProject) Array(new SbtProjectTemplate()) else Array.empty
}
