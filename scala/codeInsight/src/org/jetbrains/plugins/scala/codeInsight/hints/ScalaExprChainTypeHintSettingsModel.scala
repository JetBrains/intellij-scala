package org.jetbrains.plugins.scala.codeInsight.hints

import java.util

import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import javax.swing.JComponent
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints

import scala.collection.JavaConverters._

class ScalaExprChainTypeHintSettingsModel extends InlayProviderSettingsModel(true, "Scala.ScalaExprChainTypeHintSettingsModel") {
  // have a temporary version of the settings, so apply/cancel mechanism works
  object settings {
    private val global = ScalaCodeInsightSettings.getInstance()
    var alignExpressionChain: Boolean = _
    var hideIdenticalTypesInExpressionChain: Boolean = _

    reset()

    def reset(): Unit = {
      setEnabled(global.showExpressionChainType)
      alignExpressionChain = global.alignExpressionChain
      hideIdenticalTypesInExpressionChain = global.hideIdenticalTypesInExpressionChain
    }

    def apply(): Unit = {
      global.showExpressionChainType = isEnabled
      global.alignExpressionChain = alignExpressionChain
      global.hideIdenticalTypesInExpressionChain = hideIdenticalTypesInExpressionChain
    }

    def isModified: Boolean =
      global.showExpressionChainType != isEnabled ||
        global.alignExpressionChain != alignExpressionChain ||
        global.hideIdenticalTypesInExpressionChain != hideIdenticalTypesInExpressionChain
  }

  override def getCases: util.List[ImmediateConfigurable.Case] = Seq(
    new ImmediateConfigurable.Case(
      "Align type hints in expression chains",
      "Scala.ScalaExprChainTypeHintSettingsModel.alignExpressionChain",
      () => settings.alignExpressionChain,
      b => {
        settings.alignExpressionChain = b
        kotlin.Unit.INSTANCE
      },
      null),

    new ImmediateConfigurable.Case(
      "Hide identical types in expression chain",
      "Scala.ScalaExprChainTypeHintSettingsModel.hideIdenticalTypesInExpressionChain",
      () => settings.hideIdenticalTypesInExpressionChain,
      b => {
        settings.hideIdenticalTypesInExpressionChain = b
        kotlin.Unit.INSTANCE
      },
      null)
  ).asJava

  override def getComponent: JComponent = null

  override def getMainCheckBoxLabel: String = "Show Expr Chain Type Inlay Hints"

  override def getName: String = "Expr Chains"

  override def getPreviewText: String =
    """
      |val str = Seq(1, 2)
      |  .map(_.toDouble)
      |  .filter(_ > 0.0)
      |  .map(_.toString)
      |  .mkString
      |
      |str
      |  .filter(_ != '2')
      |  .groupBy(_.toLower)
      |  .mapValues("chars: " + _.mkString(", "))
      |  .values
      |""".stripMargin

  override def apply(): Unit = {
    settings.apply()
    ImplicitHints.updateInAllEditors()
  }

  // create a dedicated pass for the preview
  private lazy val previewPass = new ScalaExprChainTypeHintsPass {
    protected override def showExpressionChainType: Boolean = isEnabled
    override protected def alignExpressionChain: Boolean = settings.alignExpressionChain
    override protected def hideIdenticalTypesInExpressionChain: Boolean = settings.hideIdenticalTypesInExpressionChain
  }
  override def collectAndApply(editor: Editor, psiFile: PsiFile): Unit = {
    previewPass.collectExpressionChainTypeHints(editor, psiFile)
    previewPass.regenerateExprChainHints(editor, editor.getInlayModel, psiFile)
  }

  override def isModified: Boolean = settings.isModified

  override def reset(): Unit = ()
}
