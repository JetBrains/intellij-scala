package org.jetbrains.sbt.project.template.techhub

import javax.swing.Icon

import com.intellij.ide.util.projectWizard.AbstractModuleBuilder
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.scala.icons.Icons

/**
 * User: Dmitry.Naydanov
 * Date: 22.01.15.
 */
class TechHubProjectTemplate extends ProjectTemplate {
  override def getName: String = "Activator"

  override def getDescription: String = "sbt-based project from a Lightbend TechHub template"

  override def getIcon: Icon = Icons.LIGHTBEND_LOGO

  override def validateSettings(): ValidationInfo = null

  override def createModuleBuilder(): AbstractModuleBuilder = new TechHubProjectBuilder
}
