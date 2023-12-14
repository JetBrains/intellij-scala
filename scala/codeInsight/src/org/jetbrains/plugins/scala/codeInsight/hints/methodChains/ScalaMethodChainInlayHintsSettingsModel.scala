package org.jetbrains.plugins.scala.codeInsight.hints.methodChains

import java.util
import com.intellij.codeInsight.hints.{ImmediateConfigurable, InlayGroup}
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.components.labels.LinkLabel

import javax.swing.{JComponent, JPanel}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.codeInsight.hints.{ScalaHintsSettings, ScalaTypeHintsSettingsModel, navigateToInlaySettings}
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, ScalaCodeInsightSettings}
import org.jetbrains.plugins.scala.extensions.StringExt

import java.awt.{BorderLayout, FlowLayout}
import java.util.Collections

class ScalaMethodChainInlayHintsSettingsModel(project: Project) extends InlayProviderSettingsModel(
  true,
  "Scala.ScalaMethodChainInlayHintsSettingsModel",
  ScalaLanguage.INSTANCE
) {
  override def getGroup: InlayGroup = InlayGroup.METHOD_CHAINS_GROUP

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

  override def getCases: util.List[ImmediateConfigurable.Case] = Collections.emptyList()

  private val settingsPanel = new ScalaMethodChainInlaySettingsPanel(
    () => settings.alignMethodChainInlayHints,
    b => {
      settings.alignMethodChainInlayHints = b
      getOnChangeListener.settingsChanged()
    },
    () => settings.uniqueTypesToShowMethodChains,
    i => {
      settings.uniqueTypesToShowMethodChains = i
      getOnChangeListener.settingsChanged()
    }
  )

  override def getMainCheckBoxLabel: String = ScalaCodeInsightBundle.message("method.chain.hints")

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
      |""".stripMargin.withNormalizedSeparator.trim
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

  override def getDescription: String = ScalaCodeInsightBundle.message("method.chain.hints.description", ScalaCodeInsightBundle.message("xray.mode.tip", ScalaHintsSettings.xRayModeShortcut))

  override def getComponent: JComponent = {
    val linePanel = {
      val link = new LinkLabel[Any](ScalaCodeInsightBundle.message("method.chain.hints.link.to.general.settings"), null)
      link.setListener((_, _) => ScalaTypeHintsSettingsModel.navigateTo(project), null)
      val linePanel = {
        val layout = new FlowLayout()
        layout.setHgap(0)
        layout.setAlignment(FlowLayout.LEFT)
        new JPanel(layout)
      }
      linePanel.add(link)
      linePanel
    }

    val panel = new JPanel(new BorderLayout())
    panel.add(settingsPanel.getPanel, BorderLayout.NORTH)
    panel.add(linePanel, BorderLayout.SOUTH)
    panel
  }

  override def getCaseDescription(aCase: ImmediateConfigurable.Case): String = null

  override def getCasePreview(aCase: ImmediateConfigurable.Case): String = null

  override def getCasePreviewLanguage(aCase: ImmediateConfigurable.Case): Language = ScalaLanguage.INSTANCE
}

object ScalaMethodChainInlayHintsSettingsModel {
  def navigateTo(project: Project): Unit = navigateToInlaySettings[ScalaMethodChainInlayHintsSettingsModel](project)
}