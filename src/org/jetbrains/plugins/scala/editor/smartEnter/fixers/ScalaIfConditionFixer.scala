package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.psi._
import com.intellij.openapi.editor.{Editor, Document}
import editor.smartEnter.ScalaSmartEnterProcessor
import lang.psi.api.expr.{ScExpression, ScIfStmt}
import util.PsiTreeUtil

/**
 * @author Ksenia.Sautina
 * @since 1/28/13
 */

@SuppressWarnings(Array("HardCodedStringLiteral"))
class ScalaIfConditionFixer extends ScalaFixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    val ifStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScIfStmt], false)
    if (ifStatement == null) return
    val doc: Document = editor.getDocument
    val leftParenthesis = ifStatement.getLeftParenthesis.getOrElse(null)
    val rightParenthesis = ifStatement.getRightParenthesis.getOrElse(null)
    val condition = ifStatement.condition.getOrElse(null)
    if (condition == null) {
      if (leftParenthesis == null && rightParenthesis == null) {
        var stopOffset: Int = doc.getLineEndOffset(doc.getLineNumber(ifStatement.getTextRange.getStartOffset))
        val then: ScExpression = ifStatement.thenBranch.getOrElse(null)
        if (then != null) {
          stopOffset = Math.min(stopOffset, then.getTextRange.getStartOffset)
        }
        doc.replaceString(ifStatement.getTextRange.getStartOffset, stopOffset, "if () {\n}")
        processor.registerUnresolvedError(ifStatement.getTextRange.getStartOffset + "if (".length)
      }
      else if (leftParenthesis != null && rightParenthesis == null) {
        doc.insertString(ifStatement.getTextRange.getEndOffset, ") {\n}")
        processor.registerUnresolvedError(leftParenthesis.getTextRange.getEndOffset)
      } else {
        processor.registerUnresolvedError(leftParenthesis.getTextRange.getEndOffset)
      }
    } else if (rightParenthesis == null) {
      doc.insertString(condition.getTextRange.getEndOffset, ")")
    }
  }
}

