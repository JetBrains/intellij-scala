package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeHighlighting._
import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.codeInsight.daemon.impl.{DefaultHighlightInfoProcessor, HighlightInfoProcessor}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.06.2009
 */
final class ScalaUnusedImportsPassFactory
  extends MainHighlightingPassFactory
    with TextEditorHighlightingPassFactoryRegistrar {

  def registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project): Unit = {
    registrar.registerTextEditorHighlightingPass(
      this,
      Array(Pass.UPDATE_ALL),
      null,
      false,
      -1
    )
  }

  override def createHighlightingPass(file: PsiFile,
                                      editor: Editor): ScalaUnusedImportPass = {
    annotator.usageTracker.ScalaRefCountHolder.findDirtyScope(file) match {
      case Some(None) if ScalaUnusedImportPass.isUpToDate(file) || !ProblemHighlightFilter.shouldHighlightFile(file) => null
      case _ => new ScalaUnusedImportPass(file, editor, editor.getDocument, new DefaultHighlightInfoProcessor)
    }
  }

  override def createMainHighlightingPass(file: PsiFile, document: Document,
                                          highlightInfoProcessor: HighlightInfoProcessor) =
    new ScalaUnusedImportPass(file, null, document, highlightInfoProcessor)
}