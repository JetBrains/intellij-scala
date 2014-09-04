package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections


import com.intellij.codeHighlighting._
import com.intellij.codeInsight.daemon.impl.{DefaultHighlightInfoProcessor, FileStatusMap, HighlightInfoProcessor}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.06.2009
 */
class ScalaUnusedImportsPassFactory(project: Project, highlightingPassRegistrar: TextEditorHighlightingPassRegistrar)
        extends AbstractProjectComponent(project) with MainHighlightingPassFactory {
  highlightingPassRegistrar.registerTextEditorHighlightingPass(this, Array[Int](Pass.UPDATE_ALL),
    null, false, -1)

  def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = {
    val textRange: TextRange = FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL)
    if (textRange == null && ScalaUnusedImportPass.isUpToDate(file)) return null
    create(file, editor.getDocument, editor, new DefaultHighlightInfoProcessor)
  }

  override def getComponentName: String = "Scala Unused import pass factory"

  override def createMainHighlightingPass(file: PsiFile, document: Document,
                                          highlightInfoProcessor: HighlightInfoProcessor): TextEditorHighlightingPass = {
    create(file, document, null, highlightInfoProcessor)
  }

  private def create(file: PsiFile, document: Document, editor: Editor,
                     highlightInfoProcessor: HighlightInfoProcessor): TextEditorHighlightingPass = {
    new ScalaUnusedImportPass(file, editor, document, highlightInfoProcessor)
  }
}