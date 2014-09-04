package org.jetbrains.plugins.scala
package config.scalaProjectTemplate

import javax.swing.Icon

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.scala.icons.Icons

/**
 * User: Dmitry Naydanov
 * Date: 11/7/12
 */
class ScalaProjectTemplate extends ProjectTemplate {
  def getName = ScalaBundle message "scala.config.project.template.name"

  def getDescription = ScalaBundle message "scala.config.project.template.description"

  def createModuleBuilder(): ModuleBuilder = new ScalaModuleBuilder

  def validateSettings(): ValidationInfo = null

  def getIcon: Icon = Icons.SCALA_SMALL_LOGO
}
