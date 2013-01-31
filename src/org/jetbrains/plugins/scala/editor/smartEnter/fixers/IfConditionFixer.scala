package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.psi._
import com.intellij.openapi.editor.{Editor, Document}
import editor.smartEnter.ScalaSmartEnterProcessor
import lang.psi.api.expr.{ScExpression, ScIfStmt}
import util.PsiTreeUtil
import lang.psi.impl.expr.ScIfStmtImpl

/**
 * @author Ksenia.Sautina
 * @since 1/28/13
 */

@SuppressWarnings(Array("HardCodedStringLiteral"))
class IfConditionFixer extends Fixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    val ifStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScIfStmt], false)
    if (ifStatement != null) {
      val doc: Document = editor.getDocument
      val rParen = ifStatement.getRightParenthesis.getOrElse(null)
      val lParen = ifStatement.getLeftParenthesis.getOrElse(null)
      val condition: ScExpression = ifStatement.condition.getOrElse(null)
      if (condition == null) {
        if (lParen == null || rParen == null) {
          var stopOffset: Int = doc.getLineEndOffset(doc.getLineNumber(ifStatement.getTextRange.getStartOffset))
          val then: ScExpression = ifStatement.thenBranch.getOrElse(null)
          if (then != null) {
            stopOffset = Math.min(stopOffset, then.getTextRange.getStartOffset)
          }
          stopOffset = Math.min(stopOffset, ifStatement.getTextRange.getEndOffset)
          doc.replaceString(ifStatement.getTextRange.getStartOffset, stopOffset, "if () {\n}")
          processor.registerUnresolvedError(ifStatement.getTextRange.getStartOffset + "if (".length)
        }
        else {
          processor.registerUnresolvedError(lParen.getTextRange.getEndOffset)
        }
      }
      else if (rParen == null) {
        doc.insertString(condition.getTextRange.getEndOffset, ")")
      }
    }
  }

}

