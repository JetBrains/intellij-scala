package org.jetbrains.plugins.scala
package script

import javax.swing.JComponent

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

/**
 * User: Alexander Podkhalyuzin
 * Date: 05.02.2009
 */

class ScalaScriptRunConfigurationEditor(project: Project, configuration: ScalaScriptRunConfiguration)
extends SettingsEditor[ScalaScriptRunConfiguration] {
  val form = new ScalaScriptRunConfigurationForm(project, configuration)

  def resetEditorFrom(s: ScalaScriptRunConfiguration): Unit = form(s)

  def applyEditorTo(s: ScalaScriptRunConfiguration): Unit = s(form)

  def createEditor: JComponent = form.getPanel
}