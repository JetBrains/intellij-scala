package org.jetbrains.plugins.scala
package config.scalaProjectTemplate

import com.intellij.platform.{ProjectTemplate, ProjectTemplatesFactory}
import com.intellij.ide.util.projectWizard.WizardContext
import javax.swing.Icon

/**
 * User: Dmitry Naydanov
 * Date: 11/7/12
 */
class ScalaProjectTemplatesFactory extends ProjectTemplatesFactory {
  def getGroups: Array[String] = Array("Scala")

  def createTemplates(group: String, context: WizardContext): Array[ProjectTemplate] =
    if (group == "Scala") Array[ProjectTemplate](new ScalaProjectTemplate) else Array.empty

  override def getGroupIcon(group: String): Icon = org.jetbrains.plugins.scala.icons.Icons.SCALA_SMALL_LOGO
}
