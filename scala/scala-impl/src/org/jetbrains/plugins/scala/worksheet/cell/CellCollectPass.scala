package org.jetbrains.plugins.scala.worksheet.cell

import com.intellij.codeInsight.daemon.impl.{HighlightInfoProcessor, ProgressableTextEditorHighlightingPass}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile

/**
  * User: Dmitry.Naydanov
  * Date: 03.09.18.
  */
class CellCollectPass(val file: PsiFile, editor: Editor, val document: Document,
                      highlightInfoProcessor: HighlightInfoProcessor)
  extends ProgressableTextEditorHighlightingPass(file.getProject, document,
    "Scala worksheet collect cells", file, editor, file.getTextRange, true, highlightInfoProcessor) {
  override def collectInformationWithProgress(progress: ProgressIndicator): Unit = {
    val cellManager = CellManager.getInstance(myProject)

    for (i <- 0 until document.getLineCount) {
      val startOffset = document.getLineStartOffset(i)
      val endOffset = document.getLineEndOffset(i)

      if (endOffset - startOffset >= CellManager.CELL_START_MARKUP.length &&
        StringUtil.equals(document.getImmutableCharSequence.subSequence(startOffset, startOffset + CellManager.CELL_START_MARKUP.length), CellManager.CELL_START_MARKUP)
      ) {
        Option(file.findElementAt(startOffset + 1)).foreach(cellManager.processProbablyStartElement)
      }
    }
  }

  override def applyInformationWithProgress(): Unit = {}
}
