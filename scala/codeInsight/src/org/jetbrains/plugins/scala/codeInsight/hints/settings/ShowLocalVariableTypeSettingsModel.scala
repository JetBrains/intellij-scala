package org.jetbrains.plugins.scala.codeInsight.hints.settings

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, ScalaCodeInsightSettings}

class ShowLocalVariableTypeSettingsModel(group: TypeHintSettingsModelGroup, project: Project)
  extends SymbolTypeHintSettingsModelBase(group, project, "Scala.ScalaTypeHintsSettingsModel.showLocalVariableType")
{
  override protected def getSetting: Boolean =
    ScalaCodeInsightSettings.getInstance().showLocalVariableType
  override protected def setSetting(value: Boolean): Unit =
    ScalaCodeInsightSettings.getInstance().showLocalVariableType = value

  override def getDescription: String = null

  override def getName: String = ScalaCodeInsightBundle.message("local.variables")
  }
