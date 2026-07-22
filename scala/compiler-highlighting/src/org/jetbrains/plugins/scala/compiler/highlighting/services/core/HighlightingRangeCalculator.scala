package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{JavaTokenType, PsiElement, PsiFile, PsiJavaToken, PsiWhiteSpace}
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.compiler.highlighting.core.ExternalHighlighting.RangeInfo
import org.jetbrains.plugins.scala.compiler.highlighting.services.ExternalHighlightersService.{Log, TextRangeWithEndOfLine}
import org.jetbrains.plugins.scala.extensions.PsiElementExt

/**
 * Calculates and resolves text ranges for compiler diagnostics within a document.
 *
 * This component translates line and column coordinates provided by an external compiler
 * into concrete document offsets. It also ensures that the calculated ranges align
 * logically with the underlying program structure tokens where appropriate.
 */
private[highlighting] class HighlightingRangeCalculator {

  @RequiresReadLock
  def calculateRangeToHighlight(
    rangeInfo: RangeInfo,
    document: Document,
    psiFile: PsiFile
  ): Option[TextRangeWithEndOfLine] = {
    rangeInfo match {
      case range@RangeInfo.Range(PosInfo(startLine, startColumn), PosInfo(endLine, endColumn), _) =>
        for {
          startOffset <- convertToOffset(startLine, startColumn, document)
          endOffset <- convertToOffset(endLine, endColumn, document)
        } yield {
          if (startOffset <= endOffset) {
            TextRangeWithEndOfLine(textRange = TextRange.create(startOffset, endOffset), endOfLine = false)
          } else {
            val message = s"Illegal highlighting range calculated, startOffset=$startOffset, endOffset=$endOffset, range=$range"
            Log.error(message)
            throw new IllegalArgumentException(message)
          }
        }
      case RangeInfo.Pointer(PosInfo(line, column)) =>
        convertToOffset(line, column, document).flatMap { startOffset =>
          guessRangeToHighlight(psiFile, startOffset).map { textRange =>
            TextRangeWithEndOfLine(textRange = textRange, endOfLine = false)
          }.orElse {
            val endOfLine = startOffset == document.getLineEndOffset(line - 1)
            Some(TextRangeWithEndOfLine(textRange = TextRange.create(startOffset, startOffset), endOfLine))
          }
        }
    }
  }

  @RequiresReadLock
  private def guessRangeToHighlight(psiFile: PsiFile, startOffset: Int): Option[TextRange] =
    elementToHighlight(psiFile, startOffset).map(_.getTextRange)

  @RequiresReadLock
  private def elementToHighlight(file: PsiFile, offset: Int): Option[PsiElement] =
    Option(file.findElementAt(offset)).flatMap {
      case whiteSpace: PsiWhiteSpace =>
        whiteSpace.prevElementNotWhitespace
      case javaToken: PsiJavaToken if javaToken.getTokenType == JavaTokenType.DOT =>
        javaToken.nextElementNotWhitespace
      case other =>
        Some(other)
    }

  /**
   * Must be called inside a read action in order to have a correct evaluation of `Document#getLineCount`,
   * ensuring that the document has not been modified before subsequently calling `Document.getLineStartOffset`.
   *
   * @param line     1-based line index
   * @param column   1-based column index
   * @param document the document that corresponds to the line and column information, used for calculating offsets
   */
  @RequiresReadLock
  private def convertToOffset(line: Int, column: Int, document: Document): Option[Int] = {
    // Document works with 0-based line and column indices.
    val ln = line - 1
    val cl = column - 1
    if (ln >= 0 && ln < document.getLineCount && cl >= 0) Some(document.getLineStartOffset(ln) + cl)
    else None
  }
}