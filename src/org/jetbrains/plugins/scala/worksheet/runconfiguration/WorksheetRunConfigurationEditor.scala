package org.jetbrains.plugins.scala
package worksheet.runconfiguration

import com.intellij.openapi.project.Project
import com.intellij.openapi.options.SettingsEditor
import javax.swing.JComponent

/**
 * @author Ksenia.Sautina
 * @since 10/16/12
 */
class WorksheetRunConfigurationEditor(project: Project, configuration: WorksheetRunConfiguration)
  extends SettingsEditor[WorksheetRunConfiguration] {
  val form = new WorksheetRunConfigurationForm(project, configuration)

  def resetEditorFrom(s: WorksheetRunConfiguration) {
    form(s)
  }

  def disposeEditor() {}

  def applyEditorTo(s: WorksheetRunConfiguration) {
    s(form)
  }

  def createEditor: JComponent = form.getPanel
}