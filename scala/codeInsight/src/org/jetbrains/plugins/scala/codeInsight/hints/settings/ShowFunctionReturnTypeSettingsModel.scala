package org.jetbrains.plugins.scala.codeInsight.hints.settings

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, ScalaCodeInsightSettings}

class ShowFunctionReturnTypeSettingsModel(group: TypeHintSettingsModelGroup, project: Project)
  extends SymbolTypeHintSettingsModelBase(group, project, "Scala.ScalaTypeHintsSettingsModel.showMethodResultType")
{
  override protected def getSetting: Boolean =
    ScalaCodeInsightSettings.getInstance().showFunctionReturnType
  override protected def setSetting(value: Boolean): Unit =
    ScalaCodeInsightSettings.getInstance().showFunctionReturnType = value

  override def getDescription: String = null

  override def getName: String = ScalaCodeInsightBundle.message("method.results")
}
