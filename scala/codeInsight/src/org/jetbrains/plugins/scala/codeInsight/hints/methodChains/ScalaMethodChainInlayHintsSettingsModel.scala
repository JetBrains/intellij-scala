package org.jetbrains.plugins.scala.codeInsight.hints.methodChains

import java.util

import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import javax.swing.JComponent
import kotlin.Unit.{INSTANCE => kUnit}
import org.jetbrains.plugins.scala.codeInsight.hints.ScalaHintsSettings
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, ScalaCodeInsightSettings, hints}
import org.jetbrains.plugins.scala.extensions.StringExt

import scala.jdk.CollectionConverters._

class ScalaMethodChainInlayHintsSettingsModel(project: Project) extends InlayProviderSettingsModel(true, "Scala.ScalaMethodChainInlayHintsSettingsModel") {
  // have a temporary version of the settings, so apply/cancel mechanism works
  object settings {
    private val global = ScalaCodeInsightSettings.getInstance()
    var alignMethodChainInlayHints: Boolean = _
    var uniqueTypesToShowMethodChains: Int = _

    reset()

    def reset(): Unit = {
      setEnabled(global.showMethodChainInlayHints)
      alignMethodChainInlayHints = global.alignMethodChainInlayHints
      uniqueTypesToShowMethodChains = global.uniqueTypesToShowMethodChains
    }

    def apply(): Unit = {
      global.showMethodChainInlayHints = isEnabled
      global.alignMethodChainInlayHints = alignMethodChainInlayHints
      global.uniqueTypesToShowMethodChains = uniqueTypesToShowMethodChains
    }

    def isModified: Boolean =
      global.showMethodChainInlayHints != isEnabled ||
        global.alignMethodChainInlayHints != alignMethodChainInlayHints ||
        global.uniqueTypesToShowMethodChains != uniqueTypesToShowMethodChains
  }

  override def getCases: util.List[ImmediateConfigurable.Case] = SeqHasAsJava(Seq(
    new ImmediateConfigurable.Case(
      ScalaCodeInsightBundle.message("in.a.separate.column"),
      "Scala.ScalaMethodChainInlayHintsSettingsModel.alignMethodChainInlayHints",
      () => settings.alignMethodChainInlayHints,
      b => {
        settings.alignMethodChainInlayHints = b
        kUnit
      },
      null)
  )).asJava

  private val settingsPanel = new ScalaMethodChainInlaySettingsPanel(
    () => settings.uniqueTypesToShowMethodChains,
    i => {
      settings.uniqueTypesToShowMethodChains = i
      getOnChangeListener.settingsChanged()
    }
  )
  override def getComponent: JComponent = settingsPanel.getPanel

  override def getMainCheckBoxLabel: String = ScalaCodeInsightBundle.message("show.method.chain.hints")

  override def getName: String = ScalaCodeInsightBundle.message("method.chain.hints")

  override def getPreviewText: String = {
    if (project.isDefault)
      return null

    """
      |readSettings()
      |  .server
      |  .connect()
      |  .withHandshakeUpgrade
      |  .ping()
      |
      |def readSettings(): Configuration = ???
      |
      |class Configuration(val server: IPAddress)
      |trait IPAddress { def connect(): Connection }
      |trait Connection {
      |  def withHandshakeUpgrade: Connection
      |  def ping(): Int
      |}
      |""".stripMargin.withNormalizedSeparator
  }

  override def apply(): Unit = {
    settings.apply()
    ImplicitHints.updateInAllEditors()
  }

  // create a dedicated pass for the preview
  private lazy val previewPass: ScalaMethodChainInlayHintsPass = new ScalaMethodChainInlayHintsPass {
    private def globalSettings = ScalaMethodChainInlayHintsSettingsModel.this.settings

    override val settings: ScalaHintsSettings = new ScalaHintsSettings.Defaults {
      override def showMethodChainInlayHints: Boolean = true
      override def alignMethodChainInlayHints: Boolean = globalSettings.alignMethodChainInlayHints
      override def uniqueTypesToShowMethodChains: Int = globalSettings.uniqueTypesToShowMethodChains
      override def showObviousType: Boolean = true // always show obvious types in the preview
    }
  }

  override def collectAndApply(editor: Editor, psiFile: PsiFile): Unit = {
    previewPass.collectMethodChainHints(editor, psiFile)
    previewPass.regenerateMethodChainHints(editor, editor.getInlayModel, psiFile)
  }

  override def isModified: Boolean = settings.isModified

  override def reset(): Unit = {
    settings.reset()
    settingsPanel.reset()
  }
}
