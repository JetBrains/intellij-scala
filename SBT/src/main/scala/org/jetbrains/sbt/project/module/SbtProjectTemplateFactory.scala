package org.jetbrains.sbt
package project.module

import com.intellij.platform.{ProjectTemplate, ProjectTemplatesFactory}
import com.intellij.ide.util.projectWizard.WizardContext

/**
 * User: Dmitry.Naydanov
 * Date: 11.03.14.
 */
class SbtProjectTemplateFactory extends ProjectTemplatesFactory {
  override def createTemplates(group: String, context: WizardContext): Array[ProjectTemplate] =
    if (group == "Scala") Array(new SbtProjectTemplate) else Array.empty

  override def getGroups: Array[String] = Array("Scala")
}
