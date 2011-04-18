package org.jetbrains.plugins.scala.testingSupport.specs2

import com.intellij.openapi.options.SettingsEditor
import javax.swing.JComponent
import com.intellij.openapi.project.Project
/**
 * User: Alexander Podkhalyuzin
 * Date: 03.05.2009
 */

class Specs2RunConfigurationEditor(project: Project, configuration: Specs2RunConfiguration)
extends SettingsEditor[Specs2RunConfiguration] {
  val form = new Specs2RunConfigurationForm(project, configuration)

  def resetEditorFrom(s: Specs2RunConfiguration) {
    form(s)
  }

  def disposeEditor() {}

  def applyEditorTo(s: Specs2RunConfiguration) {
    s(form)
  }

  def createEditor: JComponent = form.getPanel
}