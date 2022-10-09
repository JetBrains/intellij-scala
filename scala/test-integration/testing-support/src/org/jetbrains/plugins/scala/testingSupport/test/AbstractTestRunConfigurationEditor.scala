package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.testingSupport.test.ui.TestRunConfigurationForm

import javax.swing.JComponent

class AbstractTestRunConfigurationEditor(project: Project)
  extends SettingsEditor[AbstractTestRunConfiguration] {

  private val form = new TestRunConfigurationForm(project)

  override def createEditor: JComponent = form.getPanel

  override def resetEditorFrom(configuration: AbstractTestRunConfiguration): Unit = form.resetFrom(configuration)

  override def applyEditorTo(configuration: AbstractTestRunConfiguration): Unit = form.applyTo(configuration)
}
