package org.jetbrains.sbt.runner

import javax.swing.JComponent

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

/**
 * Run configuration setting.
 */
class SbtRunConfigurationEditor(project: Project, configuration: SbtRunConfiguration)
        extends SettingsEditor[SbtRunConfiguration] {
  val form = new SbtRunConfigurationForm(project, configuration)

  def resetEditorFrom(configuration: SbtRunConfiguration): Unit = form(configuration)

  def applyEditorTo(configuration: SbtRunConfiguration): Unit = configuration(form)

  def createEditor: JComponent = form.getMainPanel
}