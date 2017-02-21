package org.jetbrains.plugins.scala
package project.template

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.platform.{ProjectTemplate, ProjectTemplatesFactory}
import org.jetbrains.plugins.dotty.project.template.DottyProjectTemplate
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.sbt.project.template.SbtProjectTemplate
import org.jetbrains.sbt.project.template.activator.ScalaActivatorProjectTemplate

/**
 * @author Pavel Fatin
 */
class ScalaProjectTemplatesFactory extends ProjectTemplatesFactory {
  def getGroups = Array("Scala")

  override def getGroupIcon(group: String) = Icons.SCALA_SMALL_LOGO

  override def createTemplates(group: String, context: WizardContext): Array[ProjectTemplate] =
    if (context.isCreatingNewProject)
      Array(new SbtProjectTemplate, new ScalaActivatorProjectTemplate, new ScalaProjectTemplate, new DottyProjectTemplate)
    else
      Array(new ScalaProjectTemplate, new DottyProjectTemplate)
}