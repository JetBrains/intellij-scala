package org.jetbrains.plugins.scala
package script

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import javax.swing.JComponent

/**
 * User: Alexander Podkhalyuzin
 * Date: 05.02.2009
 */

class ScalaScriptRunConfigurationEditor(project: Project, configuration: ScalaScriptRunConfiguration)
extends SettingsEditor[ScalaScriptRunConfiguration] {
  val form = new ScalaScriptRunConfigurationForm(project, configuration)

  def resetEditorFrom(s: ScalaScriptRunConfiguration): Unit = form(s)

  def disposeEditor: Unit = {}

  def applyEditorTo(s: ScalaScriptRunConfiguration): Unit = s(form)

  def createEditor: JComponent = form.getPanel
}