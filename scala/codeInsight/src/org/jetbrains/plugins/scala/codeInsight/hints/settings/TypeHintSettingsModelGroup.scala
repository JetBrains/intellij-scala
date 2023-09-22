package org.jetbrains.plugins.scala.codeInsight.hints.settings

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInsight.hints.ScalaHintsSettings

/*
  This Class makes it possible that all three settings models can access each other.
  This is important for their preview. All three settings have the same preview and the preview
  should reflect the settings currently enabled in the settings dialog.
 */
class TypeHintSettingsModelGroup(project: Project) {
  lazy val showFunctionReturnTypeSettingsModel = new ShowFunctionReturnTypeSettingsModel(this, project)
  lazy val showMemberVariableTypeSettingsModel = new ShowMemberVariableTypeSettingsModel(this, project)
  lazy val showLocalVariableTypeSettingsModel = new ShowLocalVariableTypeSettingsModel(this, project)

  def makePreviewSettings(): ScalaHintsSettings = new ScalaHintsSettings.Defaults {
    override def showMethodResultType: Boolean = showFunctionReturnTypeSettingsModel.isEnabled
    override def showMemberVariableType: Boolean = showMemberVariableTypeSettingsModel.isEnabled
    override def showLocalVariableType: Boolean = showLocalVariableTypeSettingsModel.isEnabled
  }
}
