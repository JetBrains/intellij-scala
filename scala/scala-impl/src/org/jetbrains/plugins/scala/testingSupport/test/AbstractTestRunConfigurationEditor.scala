package org.jetbrains.plugins.scala
package testingSupport.test

import javax.swing.JComponent

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

class AbstractTestRunConfigurationEditor(project: Project, configuration: AbstractTestRunConfiguration)
  extends SettingsEditor[AbstractTestRunConfiguration] {

  private val form = new TestRunConfigurationForm(project, configuration)

  override def resetEditorFrom(s: AbstractTestRunConfiguration): Unit = form(s)

  override def applyEditorTo(s: AbstractTestRunConfiguration): Unit = s(form)

  override def createEditor: JComponent = form.getPanel
}
