package org.jetbrains.plugins.scala
package console

import javax.swing.JComponent

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */

class ScalaConsoleRunConfigurationEditor(project: Project, configuration: ScalaConsoleRunConfiguration)
        extends SettingsEditor[ScalaConsoleRunConfiguration] {
  val form = new ScalaConsoleRunConfigurationForm(project, configuration)

  def resetEditorFrom(s: ScalaConsoleRunConfiguration) {
    form(s)
  }

  def applyEditorTo(s: ScalaConsoleRunConfiguration) {
    s(form)
  }

  def createEditor: JComponent = form.getPanel
}