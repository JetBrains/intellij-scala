package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScForStatement

/**
 * @author Ksenia.Sautina
 * @since 1/29/13
 */

@SuppressWarnings(Array("HardCodedStringLiteral"))
class ScalaForStatementFixer extends ScalaFixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    val forStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScForStatement], false)
    if (forStatement == null) return
    val doc: Document = editor.getDocument
    val leftParenthesis = forStatement.getLeftParenthesis.orNull
    val rightParenthesis = forStatement.getRightParenthesis.orNull
    val condition = forStatement.enumerators.orNull

    if (condition == null) {
      if (leftParenthesis == null && rightParenthesis == null) {
        val stopOffset: Int = doc.getLineEndOffset(doc.getLineNumber(forStatement.getTextRange.getStartOffset))
        doc.replaceString(forStatement.getTextRange.getStartOffset, stopOffset, "for () {\n}")
        processor.registerUnresolvedError(forStatement.getTextRange.getStartOffset + "for (".length)
      }
      else if (leftParenthesis != null && rightParenthesis == null) {
        doc.insertString(forStatement.getTextRange.getEndOffset, ") {\n}")
        processor.registerUnresolvedError(leftParenthesis.getTextRange.getEndOffset)
      } else {
        processor.registerUnresolvedError(leftParenthesis.getTextRange.getEndOffset)
      }
    } else if (rightParenthesis == null) {
      doc.insertString(condition.getTextRange.getEndOffset, ")")
    }
  }

}

