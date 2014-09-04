package org.jetbrains.sbt
package project.template

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.platform.ProjectTemplatesFactory
import org.jetbrains.sbt.project.template.SbtProjectTemplateFactory._

/**
 * User: Dmitry.Naydanov, Pavel Fatin
 * Date: 11.03.14.
 */
class SbtProjectTemplateFactory extends ProjectTemplatesFactory {
  override def getGroups = Array(ScalaGroup)

  override def createTemplates(group: String, context: WizardContext) = group match {
    case ScalaGroup => Array(new SbtProjectTemplate())
    case _ => Array.empty
  }
}

private object SbtProjectTemplateFactory {
  val ScalaGroup = "Scala"
}