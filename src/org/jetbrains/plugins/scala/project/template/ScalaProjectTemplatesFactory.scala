package org.jetbrains.plugins.scala
package project.template

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.platform.ProjectTemplatesFactory
import org.jetbrains.plugins.dotty.project.template.DottyProjectTemplate
import org.jetbrains.plugins.scala.icons.Icons

/**
 * @author Pavel Fatin
 */
class ScalaProjectTemplatesFactory extends ProjectTemplatesFactory {
  def getGroups = Array("Scala")

  def createTemplates(group: String, context: WizardContext) = Array(new ScalaProjectTemplate(), new DottyProjectTemplate())

  override def getGroupIcon(group: String) = Icons.SCALA_SMALL_LOGO
}

object ScalaProjectTemplatesFactory {
  val Group = "Scala"
}