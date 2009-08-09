package org.jetbrains.plugins.scala
package testingSupport
package specs


import com.intellij.openapi.options.SettingsEditor
import javax.swing.JComponent
import scalaTest.{ScalaTestRunConfigurationForm, ScalaTestRunConfiguration}
import com.intellij.openapi.project.Project
/**
 * User: Alexander Podkhalyuzin
 * Date: 03.05.2009
 */

class SpecsRunConfigurationEditor(project: Project, configuration: SpecsRunConfiguration)
extends SettingsEditor[SpecsRunConfiguration] {
  val form = new SpecsRunConfigurationForm(project, configuration)

  def resetEditorFrom(s: SpecsRunConfiguration): Unit = form(s)

  def disposeEditor: Unit = {}

  def applyEditorTo(s: SpecsRunConfiguration): Unit = s(form)

  def createEditor: JComponent = form.getPanel
}