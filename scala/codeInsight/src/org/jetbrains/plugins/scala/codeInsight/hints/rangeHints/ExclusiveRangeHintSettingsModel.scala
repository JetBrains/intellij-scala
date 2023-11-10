package org.jetbrains.plugins.scala.codeInsight.hints.rangeHints

import com.intellij.codeInsight.hints.{ImmediateConfigurable, InlayGroup}
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, ScalaCodeInsightSettings}
import org.jetbrains.plugins.scala.codeInsight.hints.{ScalaHintsSettings, navigateToInlaySettings}
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints
import org.jetbrains.plugins.scala.extensions.StringExt

import java.util
import javax.swing.{JComponent, JPanel}

class ExclusiveRangeHintSettingsModel(project: Project) extends InlayProviderSettingsModel(
  true,
  "Scala.ExclusiveRangeHintsSettingsModel",
  ScalaLanguage.INSTANCE
) {
  override def getGroup: InlayGroup = InlayGroup.VALUES_GROUP

  // have a temporary version of the settings, so apply/cancel mechanism works
  object settings {
    private val global = ScalaCodeInsightSettings.getInstance()

    reset()

    def reset(): Unit = {
      setEnabled(global.showExclusiveRangeHint)
    }

    def apply(): Unit = {
      global.showExclusiveRangeHint = isEnabled
    }

    def isModified: Boolean =
      global.showExclusiveRangeHint != isEnabled
  }

  override def getCases: util.List[ImmediateConfigurable.Case] = util.Collections.emptyList()

  override def getMainCheckBoxLabel: String = ScalaCodeInsightBundle.message("show.exclusive.range.hint")

  override def getName: String = ScalaCodeInsightBundle.message("range.exclusive.hint")

  override def getComponent: JComponent = new JPanel()

  override def getPreviewText: String = {
    if (project.isDefault)
      return null

    """
      |val r1 = Range(1, 10)
      |val r2 = Range.inclusive(1, 10)
      |""".stripMargin.withNormalizedSeparator.trim
  }

  override def apply(): Unit = {
    settings.apply()
    ImplicitHints.updateInAllEditors()
  }

  // create a dedicated pass for the preview
  private lazy val previewPass: RangeInlayHintsPass = new RangeInlayHintsPass {
    override def isPreview: Boolean = true
    override val settings: ScalaHintsSettings = new ScalaHintsSettings.Defaults {
      override def showExclusiveRangeHint: Boolean = true
    }
  }

  override def collectAndApply(editor: Editor, psiFile: PsiFile): Unit = {
    previewPass.collectRangeHints(editor, psiFile)
    previewPass.regenerateRangeInlayHints(editor, editor.getInlayModel, psiFile)
  }

  override def isModified: Boolean = settings.isModified

  override def reset(): Unit =
    settings.reset()

  override def getDescription: String = ScalaCodeInsightBundle.message("range.exclusive.hint.description", ScalaCodeInsightBundle.message("xray.mode.tip", ScalaHintsSettings.xRayModeShortcut))

  override def getCaseDescription(aCase: ImmediateConfigurable.Case): String = null

  override def getCasePreview(aCase: ImmediateConfigurable.Case): String = null

  override def getCasePreviewLanguage(aCase: ImmediateConfigurable.Case): Language = ScalaLanguage.INSTANCE
}

object ExclusiveRangeHintSettingsModel {
  def navigateTo(project: Project): Unit = navigateToInlaySettings[ExclusiveRangeHintSettingsModel](project)
}