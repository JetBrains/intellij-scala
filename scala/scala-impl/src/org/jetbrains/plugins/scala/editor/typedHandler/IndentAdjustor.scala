package org.jetbrains.plugins.scala
package editor
package typedHandler

import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import extensions._
import org.jetbrains.plugins.scala.editor.typedHandler.AutoBraceInsertionTools.isBehindPostfixExpr

trait IndentAdjustor {
  def shouldAdjustIndentAfterDot(editor: Editor): Boolean =
    isSingleCharOnLine(editor)

  def shouldAdjustIndentBecauseOfPostfix(offset: Int, element: PsiElement, document: Document, editor: Editor): Boolean =
    isSingleCharOnLine(editor) && continuesPostfixExpr(offset, element, document)

  def prepareIndentAdjustmentBeforeDot(document: Document, offset: Int): Unit = {
    // 1. to indent '.' correctly we add an identifier after the dot
    // 2. the document will then be committed in `charTyped`. This will build the correct block structure
    // 3. In `adjustIndentBeforeDotTask` we can then indent the line correctly
    // 4. At the end we have to remove the x, which we have inserted
    document.insertString(offset, "x")
  }

  def adjustIndentBeforeDot(editor: Editor)(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
    adjustIndent(document, project, element, offset)
    val newCaretOffset = editor.offset
    document.deleteString(newCaretOffset, newCaretOffset + 1)
  }

  //noinspection ScalaUnusedSymbol
  def adjustIndent(document: Document, project: Project, element: PsiElement, offset: Int): Unit = {
    val file = element.getContainingFile
    val dotOffset = offset - 1
    CodeStyleManager.getInstance(project).adjustLineIndent(file, dotOffset)
  }

  private def isSingleCharOnLine(editor: Editor): Boolean = {
    val document = editor.getDocument
    val offset = editor.offset
    val lineStart = document.lineStartOffset(offset)

    val prefix =
      if (lineStart < offset)
        document.getImmutableCharSequence.substring(lineStart, offset - 1)
      else ""
    val suffix = document.getImmutableCharSequence.substring(offset, document.lineEndOffset(offset))

    (prefix + suffix).forall(_.isWhitespace)
  }

  def continuesPostfixExpr(offset: Int, element: PsiElement, document: Document): Boolean = {
    val caretLine = document.getLineNumber(offset)
    val lastElementLine = document.getLineNumber(element.startOffset)
    (caretLine - lastElementLine == 1) &&
      isBehindPostfixExpr(element)
  }
}
