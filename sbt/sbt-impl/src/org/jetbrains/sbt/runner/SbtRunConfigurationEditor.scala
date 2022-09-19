package org.jetbrains.sbt.runner

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

import javax.swing.JComponent

/**
 * Run configuration setting.
 */
class SbtRunConfigurationEditor(project: Project, configuration: SbtRunConfiguration)
        extends SettingsEditor[SbtRunConfiguration] {
  val form = new SbtRunConfigurationForm(project, configuration)

  override def resetEditorFrom(configuration: SbtRunConfiguration): Unit = form(configuration)

  override def applyEditorTo(configuration: SbtRunConfiguration): Unit = configuration(form)

  override def createEditor: JComponent = form.getMainPanel
}