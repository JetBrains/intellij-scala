package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScIfStmt}

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
    val leftParenthesis = ifStatement.getLeftParenthesis.orNull
    val rightParenthesis = ifStatement.getRightParenthesis.orNull
    val condition = ifStatement.condition.orNull
    if (condition == null) {
      if (leftParenthesis == null && rightParenthesis == null) {
        var stopOffset: Int = doc.getLineEndOffset(doc.getLineNumber(ifStatement.getTextRange.getStartOffset))
        val thenBranch: ScExpression = ifStatement.thenBranch.orNull
        if (thenBranch != null) {
          stopOffset = Math.min(stopOffset, thenBranch.getTextRange.getStartOffset)
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

