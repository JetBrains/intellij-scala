package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.codeHighlighting._
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.codeInsight.hints.ScalaHintsSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ImplicitHintsPassFactory
  extends TextEditorHighlightingPassFactory
    with TextEditorHighlightingPassFactoryRegistrar {

  def registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project): Unit = {
    val runAfterAnnotator = Array(Pass.UPDATE_ALL)

    registrar.registerTextEditorHighlightingPass(this, runAfterAnnotator, null, false, -1)
  }

  override def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = file match {
    case file: ScalaFile if !ImplicitHints.isUpToDate(editor, file) =>
      new ImplicitHintsPass(editor, file, new ScalaHintsSettings.CodeInsightSettingsAdapter)
    case _ =>
      null
  }
}
