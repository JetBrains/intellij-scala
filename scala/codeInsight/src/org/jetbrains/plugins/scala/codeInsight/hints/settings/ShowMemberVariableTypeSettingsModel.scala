package org.jetbrains.plugins.scala.codeInsight.hints.settings

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, ScalaCodeInsightSettings}

class ShowMemberVariableTypeSettingsModel(group: TypeHintSettingsModelGroup, project: Project)
  extends SymbolTypeHintSettingsModelBase(group, project, "Scala.ScalaTypeHintsSettingsModel.showMemberVariableType")
{
  override protected def getSetting: Boolean =
    ScalaCodeInsightSettings.getInstance().showPropertyType
  override protected def setSetting(value: Boolean): Unit =
    ScalaCodeInsightSettings.getInstance().showPropertyType = value

  override def getDescription: String = null

  override def getName: String = ScalaCodeInsightBundle.message("member.variables")
}
