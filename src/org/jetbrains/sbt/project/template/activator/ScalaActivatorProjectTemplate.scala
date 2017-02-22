package org.jetbrains.sbt.project.template.activator

import javax.swing.Icon

import com.intellij.ide.util.projectWizard.AbstractModuleBuilder
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.scala.icons.Icons

/**
 * User: Dmitry.Naydanov
 * Date: 22.01.15.
 */
class ScalaActivatorProjectTemplate extends ProjectTemplate {
  override def getName: String = "Activator"

  override def getIcon: Icon = Icons.LIGHTBEND_LOGO

  override def getDescription: String = "Project based on Lightbend Activator templates"

  override def validateSettings(): ValidationInfo = null

  override def createModuleBuilder(): AbstractModuleBuilder = new ActivatorProjectBuilder
}
