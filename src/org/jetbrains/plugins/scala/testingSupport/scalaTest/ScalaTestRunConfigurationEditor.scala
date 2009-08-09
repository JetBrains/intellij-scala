package org.jetbrains.plugins.scala
package testingSupport
package scalaTest

import _root_.javax.swing.JComponent
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import script.{ScalaScriptRunConfigurationForm, ScalaScriptRunConfiguration}
/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaTestRunConfigurationEditor(project: Project, configuration: ScalaTestRunConfiguration)
extends SettingsEditor[ScalaTestRunConfiguration] {
  val form = new ScalaTestRunConfigurationForm(project, configuration)

  def resetEditorFrom(s: ScalaTestRunConfiguration): Unit = form(s)

  def disposeEditor: Unit = {}

  def applyEditorTo(s: ScalaTestRunConfiguration): Unit = s(form)

  def createEditor: JComponent = form.getPanel
}