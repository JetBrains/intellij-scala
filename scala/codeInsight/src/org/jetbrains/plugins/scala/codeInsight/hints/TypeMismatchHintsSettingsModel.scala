package org.jetbrains.plugins.scala.codeInsight.hints

import java.util

import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import javax.swing.JComponent
import org.jetbrains.plugins.scala.annotator.TypeMismatchHints
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

class TypeMismatchHintsSettingsModel(project: Project) extends InlayProviderSettingsModel(true, "Scala.TypeMismatchHintsSettingsModel") {
  object settings {
    private val global = ScalaProjectSettings.getInstance(project)

    reset()

    def reset(): Unit = {
      setEnabled(global.isTypeMismatchHints)
    }

    def apply(): Unit = {
      global.setTypeMismatchHints(isEnabled)
    }

    def isModified: Boolean =
      global.isTypeMismatchHints != isEnabled
  }

  override def getCases: util.List[ImmediateConfigurable.Case] = util.Collections.emptyList()

  override def getComponent: JComponent = null

  override def getMainCheckBoxLabel: String = "Show type mismatch error with inlay hints"

  override def getName: String = "Type mismatches"

  override def getPreviewText: String = null

  override def apply(): Unit = {
    settings.apply()
    TypeMismatchHints.refreshIn(project)
  }

  override def collectAndApply(editor: Editor, psiFile: PsiFile): Unit = ()

  override def isModified: Boolean = settings.isModified

  override def reset(): Unit = {
    settings.reset()
  }
}
