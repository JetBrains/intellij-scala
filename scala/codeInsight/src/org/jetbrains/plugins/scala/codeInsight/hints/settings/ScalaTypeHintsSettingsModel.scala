package org.jetbrains.plugins.scala.codeInsight.hints.settings

import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.codeInsight.hints.{ImmediateConfigurable, InlayGroup}
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import kotlin.Unit.{INSTANCE => kUnit}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.codeInsight.implicits.{ImplicitHints, ImplicitHintsPass}
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, ScalaCodeInsightSettings, hints}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import java.util
import javax.swing.{JComponent, JPanel}
import scala.jdk.CollectionConverters._

//noinspection UnstableApiUsage
class ScalaTypeHintsSettingsModel(project: Project) extends InlayProviderSettingsModel(
  true,
  "Scala.ScalaTypeHintsSettingsModel",
  ScalaLanguage.INSTANCE
) {
  override def getGroup: InlayGroup = InlayGroup.TYPES_GROUP

  // have a temporary version of the settings, so apply/cancel mechanism works
  object settings {
    private val global = ScalaCodeInsightSettings.getInstance()
    var showMethodResultType: Boolean = _
    var showMemberVariableType: Boolean = _
    var showLocalVariableType: Boolean = _

    reset()

    def reset(): Unit = {
      setEnabled(global.showTypeHints)
      showMethodResultType = global.showFunctionReturnType
      showMemberVariableType = global.showPropertyType
      showLocalVariableType = global.showLocalVariableType
    }

    def apply(): Unit = {
      global.showTypeHints = isEnabled
      global.showFunctionReturnType = showMethodResultType
      global.showPropertyType = showMemberVariableType
      global.showLocalVariableType = showLocalVariableType
    }

    def isModified: Boolean =
      global.showTypeHints != isEnabled ||
        global.showFunctionReturnType != showMethodResultType ||
        global.showPropertyType != showMemberVariableType ||
        global.showLocalVariableType != showLocalVariableType
  }

  override def getCases: util.List[ImmediateConfigurable.Case] = Seq(
    new ImmediateConfigurable.Case(
      ScalaCodeInsightBundle.message("member.variables"),
      "Scala.ScalaTypeHintsSettingsModel.showMemberVariableType",
      () => settings.showMemberVariableType,
      b => {
        settings.showMemberVariableType = b
        kUnit
      },
      null),
    new ImmediateConfigurable.Case(
      ScalaCodeInsightBundle.message("local.variables"),
      "Scala.ScalaTypeHintsSettingsModel.showLocalVariableType",
      () => settings.showLocalVariableType,
      b => {
        settings.showLocalVariableType = b
        kUnit
      },
      null),
    new ImmediateConfigurable.Case(
      ScalaCodeInsightBundle.message("method.results"),
      "Scala.ScalaTypeHintsSettingsModel.showMethodResultType",
      () => settings.showMethodResultType,
      b => {
        settings.showMethodResultType = b
        kUnit
      },
      null)
  ).asJava

  override def getComponent: JComponent = new JPanel()

  override def getMainCheckBoxLabel: String = ScalaCodeInsightBundle.message("show.type.hints.for")

  override def getName: String = ScalaCodeInsightBundle.message("type.hints")

  override def getPreviewText: String = {
    if (project.isDefault)
      return null

    """
      |class Person {
      |  val birthYear = 5 + 5
      |
      |  def ageInYear(year: Int) = {
      |    val diff = year - birthYear
      |    math.max(0, diff)
      |  }
      |}
      |""".stripMargin.withNormalizedSeparator.trim
  }

  override def apply(): Unit = {
    settings.apply()
    ImplicitHints.updateInAllEditors()
  }

  override def collectAndApply(editor: Editor, psiFile: PsiFile): Unit = {
    val previewPass = new ImplicitHintsPass(editor, psiFile.asInstanceOf[ScalaFile], new hints.ScalaHintsSettings.Defaults {
      override def showMethodResultType: Boolean = settings.showMethodResultType
      override def showMemberVariableType: Boolean = settings.showMemberVariableType
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

  override def getDescription: String = null

  override def getCaseDescription(aCase: ImmediateConfigurable.Case): String = null

  override def getCasePreview(aCase: ImmediateConfigurable.Case): String = null

  override def getCasePreviewLanguage(aCase: ImmediateConfigurable.Case): Language = ScalaLanguage.INSTANCE
}
