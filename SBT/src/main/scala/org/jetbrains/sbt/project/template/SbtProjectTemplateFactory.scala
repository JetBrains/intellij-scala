package org.jetbrains.sbt
package project.template

import com.intellij.platform.ProjectTemplatesFactory
import com.intellij.ide.util.projectWizard.WizardContext
import org.jetbrains.plugins.scala.configuration.template.ScalaProjectTemplatesFactory

/**
 * User: Dmitry.Naydanov, Pavel Fatin
 * Date: 11.03.14.
 */
class SbtProjectTemplateFactory extends ProjectTemplatesFactory {
  override def getGroups = Array(ScalaProjectTemplatesFactory.Group)

  override def createTemplates(group: String, context: WizardContext) = group match {
    case ScalaProjectTemplatesFactory.Group => Array(new SbtProjectTemplate())
    case _ => Array.empty
  }
}
