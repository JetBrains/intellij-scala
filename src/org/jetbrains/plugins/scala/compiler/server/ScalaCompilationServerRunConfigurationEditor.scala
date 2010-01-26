package org.jetbrains.plugins.scala.compiler.server

import javax.swing.JComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.options.SettingsEditor

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.01.2010
 */

class ScalaCompilationServerRunConfigurationEditor(project: Project, configuration: ScalaCompilationServerRunConfiguration)
extends SettingsEditor[ScalaCompilationServerRunConfiguration] {
  val form = new ScalaCompilationServerRunConfigurationForm(project, configuration)

  def resetEditorFrom(s: ScalaCompilationServerRunConfiguration): Unit = form(s)

  def disposeEditor: Unit = {}

  def applyEditorTo(s: ScalaCompilationServerRunConfiguration): Unit = s(form)

  def createEditor: JComponent = form.getPanel
}