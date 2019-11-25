package org.jetbrains.plugins.scala.codeInsight.hints

import java.util

import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import javax.swing.JComponent
import kotlin.Unit.{INSTANCE => kUnit}
import org.jetbrains.plugins.scala.codeInsight.implicits.{ImplicitHints, ImplicitHintsPass}
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightSettings, hints}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.collection.JavaConverters._

class ScalaTypeHintsSettingsModel(project: Project) extends InlayProviderSettingsModel(true, "Scala.ScalaTypeHintsSettingsModel") {
  // have a temporary version of the settings, so apply/cancel mechanism works
  object settings {
    private val global = ScalaCodeInsightSettings.getInstance()
    var showFunctionReturnType: Boolean = _
    var showPropertyType: Boolean = _
    var showLocalVariableType: Boolean = _

    reset()

    def reset(): Unit = {
      setEnabled(global.showTypeHints)
      showFunctionReturnType = global.showFunctionReturnType
      showPropertyType = global.showPropertyType
      showLocalVariableType = global.showLocalVariableType
    }

    def apply(): Unit = {
      global.showTypeHints = isEnabled
      global.showFunctionReturnType = showFunctionReturnType
      global.showPropertyType = showPropertyType
      global.showLocalVariableType = showLocalVariableType
    }

    def isModified: Boolean =
      global.showTypeHints != isEnabled ||
        global.showFunctionReturnType != showFunctionReturnType ||
        global.showPropertyType != showPropertyType ||
        global.showLocalVariableType != showLocalVariableType
  }

  override def getCases: util.List[ImmediateConfigurable.Case] = Seq(
    new ImmediateConfigurable.Case(
      "Member variables",
      "Scala.ScalaTypeHintsSettingsModel.showPropertyType",
      () => settings.showPropertyType,
      b => {
        settings.showPropertyType = b
        kUnit
      },
      null),
    new ImmediateConfigurable.Case(
      "Method results",
      "Scala.ScalaTypeHintsSettingsModel.showFunctionReturnType",
      () => settings.showFunctionReturnType,
      b => {
        settings.showFunctionReturnType = b
        kUnit
      },
      null),
    new ImmediateConfigurable.Case(
      "Local variables",
      "Scala.ScalaTypeHintsSettingsModel.showLocalVariableType",
      () => settings.showLocalVariableType,
      b => {
        settings.showLocalVariableType = b
        kUnit
      },
      null)
  ).asJava

  override def getComponent: JComponent = null

  override def getMainCheckBoxLabel: String = "Show type hints for:"

  override def getName: String = "Type hints"

  override def getPreviewText: String = {
    if (project.isDefault)
      return null

    """
      |class Person {
      |  // member variable
      |  val birthYear = 5 + 5
      |
      |  // method result
      |  def ageInYear(year: Int) = {
      |
      |    // local variable
      |    val diff = year - birthYear
      |
      |    math.max(0, diff)
      |  }
      |}
      |""".stripMargin.withNormalizedSeparator
  }

  override def apply(): Unit = {
    settings.apply()
    ImplicitHints.updateInAllEditors()
  }

  override def collectAndApply(editor: Editor, psiFile: PsiFile): Unit = {
    val previewPass = new ImplicitHintsPass(editor, psiFile.asInstanceOf[ScalaFile], new hints.ScalaHintsSettings.Defaults {
      override def showFunctionReturnType: Boolean = settings.showFunctionReturnType
      override def showPropertyType: Boolean = settings.showPropertyType
      override def showLocalVariableType: Boolean = settings.showLocalVariableType
      override def showMethodChainInlayHints: Boolean = false
      override def showObviousType: Boolean = true // always show obvious types in the preview
    })
    previewPass.doCollectInformation(DumbProgressIndicator.INSTANCE)
    previewPass.doApplyInformationToEditor()
  }

  override def isModified: Boolean = settings.isModified

  override def reset(): Unit = {
    settings.reset()
  }
}
