package org.jetbrains.plugins.scala
package console

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import javax.swing.JComponent

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */

class ScalaScriptConsoleRunConfigurationEditor(project: Project, configuration: ScalaScriptConsoleRunConfiguration)
        extends SettingsEditor[ScalaScriptConsoleRunConfiguration] {
  val form = new ScalaScriptConsoleRunConfigurationForm(project, configuration)

  def resetEditorFrom(s: ScalaScriptConsoleRunConfiguration): Unit = form(s)

  def disposeEditor: Unit = {}

  def applyEditorTo(s: ScalaScriptConsoleRunConfiguration): Unit = s(form)

  def createEditor: JComponent = form.getPanel
}