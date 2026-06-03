package org.jetbrains.plugins.scala.console.configuration

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

import javax.swing.JComponent

class ScalaConsoleRunConfigurationEditor(project: Project, configuration: ScalaConsoleRunConfiguration)
  extends SettingsEditor[ScalaConsoleRunConfiguration] {

  private val form = new ScalaConsoleRunConfigurationForm(project, configuration)

  override def resetEditorFrom(s: ScalaConsoleRunConfiguration): Unit = form(s)
  override def applyEditorTo(s: ScalaConsoleRunConfiguration) : Unit = s(form)
  override def createEditor: JComponent = form.getPanel
}