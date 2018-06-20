package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.codeHighlighting.{Pass, TextEditorHighlightingPass, TextEditorHighlightingPassFactory, TextEditorHighlightingPassRegistrar}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ImplicitHintsPassFactory(project: Project, registrar: TextEditorHighlightingPassRegistrar)
  extends AbstractProjectComponent(project) with TextEditorHighlightingPassFactory {

  private val runAfterAnnotator = Array(Pass.UPDATE_ALL)

  registrar.registerTextEditorHighlightingPass(this, runAfterAnnotator, null, false, -1)

  override def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = file match {
    case file: ScalaFile if !ImplicitHints.isUpToDate(editor, file) => new ImplicitHintsPass(editor, file)
    case _ => null
  }
}
