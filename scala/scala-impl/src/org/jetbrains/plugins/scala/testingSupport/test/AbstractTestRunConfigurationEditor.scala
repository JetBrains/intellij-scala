package org.jetbrains.plugins.scala
package testingSupport.test

import javax.swing.JComponent

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.05.2009
 */

class AbstractTestRunConfigurationEditor (project: Project, configuration: AbstractTestRunConfiguration)
        extends SettingsEditor[AbstractTestRunConfiguration] {
  val form = new TestRunConfigurationForm(project, configuration)

  def resetEditorFrom(s: AbstractTestRunConfiguration) {
    form(s)
  }

  def applyEditorTo(s: AbstractTestRunConfiguration) {
    s(form)
  }

  def createEditor: JComponent = form.getPanel
}
