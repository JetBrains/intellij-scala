package org.jetbrains.plugins.scala.codeInsight.hints.methodChains

import java.util

import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import javax.swing.JComponent
import kotlin.Unit.{INSTANCE => kUnit}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints

import scala.collection.JavaConverters._

class ScalaMethodChainInlayHintsSettingsModel extends InlayProviderSettingsModel(true, "Scala.ScalaMethodChainInlayHintsSettingsModel") {
  // have a temporary version of the settings, so apply/cancel mechanism works
  object settings {
    private val global = ScalaCodeInsightSettings.getInstance()
    var alignMethodChainInlayHints: Boolean = _
    var hideIdenticalTypesInMethodChains: Boolean = _
    var uniqueTypesToShowMethodChains: Int = _

    reset()

    def reset(): Unit = {
      setEnabled(global.showMethodChainInlayHints)
      alignMethodChainInlayHints = global.alignMethodChainInlayHints
      hideIdenticalTypesInMethodChains = global.hideIdenticalTypesInMethodChains
      uniqueTypesToShowMethodChains = global.uniqueTypesToShowMethodChains
    }

    def apply(): Unit = {
      global.showMethodChainInlayHints = isEnabled
      global.alignMethodChainInlayHints = alignMethodChainInlayHints
      global.hideIdenticalTypesInMethodChains = hideIdenticalTypesInMethodChains
      global.uniqueTypesToShowMethodChains = uniqueTypesToShowMethodChains
    }

    def isModified: Boolean =
      global.showMethodChainInlayHints != isEnabled ||
        global.alignMethodChainInlayHints != alignMethodChainInlayHints ||
        global.hideIdenticalTypesInMethodChains != hideIdenticalTypesInMethodChains ||
        global.uniqueTypesToShowMethodChains != uniqueTypesToShowMethodChains
  }

  override def getCases: util.List[ImmediateConfigurable.Case] = Seq(
    new ImmediateConfigurable.Case(
      "Align type hints in method chains",
      "Scala.ScalaMethodChainInlayHintsSettingsModel.alignMethodChainInlayHints",
      () => settings.alignMethodChainInlayHints,
      b => {
        settings.alignMethodChainInlayHints = b
        kUnit
      },
      null),

    new ImmediateConfigurable.Case(
      "Hide identical types in method chains",
      "Scala.ScalaMethodChainInlayHintsSettingsModel.hideIdenticalTypesInMethodChains",
      () => settings.hideIdenticalTypesInMethodChains,
      b => {
        settings.hideIdenticalTypesInMethodChains = b
        kUnit
      },
      null)
  ).asJava

  private val settingsPanel = new ScalaMethodChainInlaySettingsPanel(
    () => settings.uniqueTypesToShowMethodChains,
    i => {
      settings.uniqueTypesToShowMethodChains = i
      getOnChangeListener.settingsChanged()
    }
  )
  override def getComponent: JComponent = settingsPanel.getPanel

  override def getMainCheckBoxLabel: String = "Show method chain hints"

  override def getName: String = "Method chains"

  override def getPreviewText: String =
    """
      |Seq(1, 2, 3).foreach(println)
      |
      |Seq(1, 2, 3)
      |  .map(_.toString)
      |  .toSet
      |
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
      |  .toSeq
      |""".stripMargin

  override def apply(): Unit = {
    settings.apply()
    ImplicitHints.updateInAllEditors()
  }

  // create a dedicated pass for the preview
  private lazy val previewPass = new ScalaMethodChainInlayHintsPass {
    protected override def showMethodChainInlayHints: Boolean = isEnabled
    override protected def alignMethodChainInlayHints: Boolean = settings.alignMethodChainInlayHints
    override protected def hideIdenticalTypesInMethodChains: Boolean = settings.hideIdenticalTypesInMethodChains
    override protected def uniqueTypesToShowMethodChains: Int = settings.uniqueTypesToShowMethodChains
    override protected def showObviousTypes: Boolean = true // always show obvious types in the preview
  }

  override def collectAndApply(editor: Editor, psiFile: PsiFile): Unit = {
    previewPass.collectMethodChainHints(editor, psiFile)
    previewPass.regenerateMethodChainHints(editor, editor.getInlayModel, psiFile)
  }

  override def isModified: Boolean = settings.isModified

  override def reset(): Unit = ()
}
