package org.jetbrains.plugins.scala.codeInsight.hints.settings

import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.codeInsight.hints.{ImmediateConfigurable, InlayGroup}
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.components.labels.LinkLabel
import org.jetbrains.plugins.scala.annotator.TypeMismatchHints
import org.jetbrains.plugins.scala.codeInsight.hints.ScalaHintsSettings
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHintsPass
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, ScalaCodeInsightSettings, hints}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.{DesktopUtils, ScalaLanguage}

import java.awt.{BorderLayout, FlowLayout}
import java.util
import javax.swing.{JComponent, JPanel}

//noinspection UnstableApiUsage
abstract class BooleanTypeHintSettingsModelBase(project: Project, id: String)
  extends InlayProviderSettingsModel(true, id, ScalaLanguage.INSTANCE)
{
  protected def getSetting: Boolean
  protected def setSetting(value: Boolean): Unit
  //protected def makePreviewSettings(value: Boolean): ScalaHintsSettings
  override def getGroup: InlayGroup = InlayGroup.TYPES_GROUP
  override def getCases: util.List[ImmediateConfigurable.Case] = util.Collections.emptyList()
  override def getMainCheckBoxLabel: String = getName
  override def getComponent: JComponent = new JPanel()

  override def apply(): Unit = {
    setSetting(isEnabled)
    TypeMismatchHints.refreshIn(project)
  }

  /*override def collectAndApply(editor: Editor, psiFile: PsiFile): Unit = {
    val settings = makePreviewSettings(isEnabled)
    val previewPass = new ImplicitHintsPass(editor, psiFile.asInstanceOf[ScalaFile], settings)
    previewPass.doCollectInformation(DumbProgressIndicator.INSTANCE)
    previewPass.doApplyInformationToEditor()
  }*/

  override def isModified: Boolean = getSetting != isEnabled

  override def reset(): Unit = {
    setEnabled(getSetting)
  }

  override def getCaseDescription(aCase: ImmediateConfigurable.Case): String = null

  override def getCasePreview(aCase: ImmediateConfigurable.Case): String = null

  override def getCasePreviewLanguage(aCase: ImmediateConfigurable.Case): Language = ScalaLanguage.INSTANCE
}
