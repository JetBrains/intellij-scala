package org.jetbrains.sbt
package project.template

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.platform.ProjectTemplatesFactory
import org.jetbrains.plugins.scala.project.template.ScalaProjectTemplatesFactory
import org.jetbrains.sbt.project.template.activator.ScalaActivatorProjectTemplate

/**
 * User: Dmitry.Naydanov, Pavel Fatin
 * Date: 11.03.14.
 */
class SbtProjectTemplateFactory extends ProjectTemplatesFactory {
  override def getGroups = Array(ScalaProjectTemplatesFactory.Group)

  override def createTemplates(group: String, context: WizardContext) =
    if (context.isCreatingNewProject) Array(new SbtProjectTemplate(), new ScalaActivatorProjectTemplate) else Array.empty
}
