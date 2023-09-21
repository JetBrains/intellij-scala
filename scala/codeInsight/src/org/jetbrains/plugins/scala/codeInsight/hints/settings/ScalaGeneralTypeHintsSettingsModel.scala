package org.jetbrains.plugins.scala.codeInsight.hints.settings

import com.intellij.codeInsight.hints.settings.{InlayProviderSettingsModel, InlaySettingsConfigurableKt}
import com.intellij.codeInsight.hints.{ImmediateConfigurable, InlayGroup}
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, ScalaCodeInsightSettings}
import org.jetbrains.plugins.scala.extensions.ObjectExt

import java.util
import javax.swing.JComponent

//noinspection UnstableApiUsage
class ScalaGeneralTypeHintsSettingsModel extends InlayProviderSettingsModel(
  true,
  "Scala.ScalaGeneralTypeHintsSettingsModel",
  ScalaLanguage.INSTANCE
) {
  override def getGroup: InlayGroup = InlayGroup.TYPES_GROUP

  object settings {
    private val global = ScalaCodeInsightSettings.getInstance()

    var preserveIndents: Boolean = _
    var presentationLength: Int = _

    reset()

    def reset(): Unit = {
      setEnabled(global.showObviousType)
      preserveIndents = global.preserveIndents
      presentationLength = global.presentationLength
    }

    def apply(): Unit = {
      global.showObviousType = isEnabled
      global.preserveIndents = preserveIndents
      global.presentationLength = presentationLength
    }

    def isModified: Boolean =
      global.showObviousType != isEnabled ||
        global.preserveIndents != preserveIndents ||
        global.presentationLength != presentationLength
  }

  override def getCases: util.List[ImmediateConfigurable.Case] = util.Collections.emptyList()

  private lazy val generalSettingsPanel = new GeneralSettingsPanel(
    () => isEnabled, setEnabled(_),
    () => settings.preserveIndents, settings.preserveIndents = _,
    () => settings.presentationLength, settings.presentationLength = _
  )

  override def getComponent: JComponent = generalSettingsPanel.getPanel

  // TODO Should be no "main check box" for "settings"
  override def getMainCheckBoxLabel: String = ScalaCodeInsightBundle.message("show.types.even.if.they.are.obvious")

  override def getName: String = ScalaCodeInsightBundle.message("general.settings")

  override def getPreviewText: String = null

  override def apply(): Unit = {
    settings.apply()
    ImplicitHints.updateInAllEditors()
  }

  override def collectAndApply(editor: Editor, psiFile: PsiFile): Unit = ()

  override def isModified: Boolean = settings.isModified

  override def reset(): Unit = {
    settings.reset()
    generalSettingsPanel.reset()
  }

  override def getDescription: String = ScalaCodeInsightBundle.message("general.settings.description")

  override def getCaseDescription(aCase: ImmediateConfigurable.Case): String = null

  override def getCasePreview(aCase: ImmediateConfigurable.Case): String = null

  override def getCasePreviewLanguage(aCase: ImmediateConfigurable.Case): Language = ScalaLanguage.INSTANCE
}

object ScalaGeneralTypeHintsSettingsModel {
  def navigateTo(project: Project): Unit = {
    InlaySettingsConfigurableKt.showInlaySettings(project, ScalaLanguage.INSTANCE, _.is[ScalaGeneralTypeHintsSettingsModel])
  }
}