package org.jetbrains.plugins.scala
package config.scalaProjectTemplate

import com.intellij.platform.ProjectTemplate
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ide.util.projectWizard.ModuleBuilder

/**
 * User: Dmitry Naydanov
 * Date: 11/7/12
 */
class ScalaProjectTemplate extends ProjectTemplate {
  def getName = ScalaBundle message "scala.config.project.template.name"

  def getDescription = ScalaBundle message "scala.config.project.template.description"

  def createModuleBuilder(): ModuleBuilder = new ScalaModuleBuilder

  def validateSettings(): ValidationInfo = null
}
