package org.jetbrains.sbt.project.template.activator

import javax.swing.Icon

import com.intellij.ide.util.projectWizard.AbstractModuleBuilder
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectTemplate
import org.jetbrains.sbt.Sbt

/**
 * User: Dmitry.Naydanov
 * Date: 22.01.15.
 */
class ScalaActivatorProjectTemplate extends ProjectTemplate {
  override def getName: String = "Activator"

  override def getIcon: Icon = Sbt.Icon

  override def getDescription: String = "Project based on Typesafe activator templates"

  override def validateSettings(): ValidationInfo = null

  override def createModuleBuilder(): AbstractModuleBuilder = new ActivatorProjectBuilder
}
