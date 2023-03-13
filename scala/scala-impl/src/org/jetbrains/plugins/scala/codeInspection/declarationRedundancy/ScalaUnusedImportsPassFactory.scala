package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeHighlighting._
import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.codeInsight.daemon.impl.{DefaultHighlightInfoProcessor, FileStatusMap, HighlightInfoProcessor}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

final class ScalaUnusedImportsPassFactory
  extends TextEditorHighlightingPassFactory
    with TextEditorHighlightingPassFactoryRegistrar
    with MainHighlightingPassFactory {

  override def registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project): Unit = {
    registrar.registerTextEditorHighlightingPass(
      this,
      Array(Pass.UPDATE_ALL),
      null,
      false,
      -1
    )
  }

  override def createHighlightingPass(file: PsiFile, editor: Editor): ScalaUnusedImportPass = {
    val dirtyRange = FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL)
    val nothingChangedInFile = dirtyRange == null && (ScalaUnusedImportPass.isUpToDate(file) || !ProblemHighlightFilter.shouldHighlightFile(file))
    if (nothingChangedInFile)
      null
    else
      new ScalaUnusedImportPass(file, editor, editor.getDocument, new DefaultHighlightInfoProcessor)
  }

  override def createMainHighlightingPass(file: PsiFile, document: Document,
                                          highlightInfoProcessor: HighlightInfoProcessor) =
    new ScalaUnusedImportPass(file, null, document, highlightInfoProcessor)
}