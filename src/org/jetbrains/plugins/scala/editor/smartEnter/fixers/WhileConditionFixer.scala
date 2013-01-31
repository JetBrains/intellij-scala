package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.psi._
import com.intellij.openapi.editor.{Editor, Document}
import editor.smartEnter.ScalaSmartEnterProcessor
import util.PsiTreeUtil
import lang.psi.api.expr.ScWhileStmt

/**
 * @author Ksenia.Sautina
 * @since 1/30/13
 */

@SuppressWarnings(Array("HardCodedStringLiteral"))
class WhileConditionFixer extends Fixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    val whileStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScWhileStmt], false)
    if (whileStatement != null) {
      val doc: Document = editor.getDocument
      val rParenth  = whileStatement.getRightParenthesis.getOrElse(null)
      val lParenth  = whileStatement.getLeftParenthesis.getOrElse(null)
      val condition = whileStatement.condition.getOrElse(null)
      if (condition == null) {
        if (lParenth == null || rParenth == null) {
          var stopOffset: Int = doc.getLineEndOffset(doc.getLineNumber(whileStatement.getTextRange.getStartOffset))
          val block = whileStatement.body.getOrElse(null)
          if (block != null) {
            stopOffset = Math.min(stopOffset, block.getTextRange.getStartOffset)
          }
          stopOffset = Math.min(stopOffset, whileStatement.getTextRange.getEndOffset)
          doc.replaceString(whileStatement.getTextRange.getStartOffset, stopOffset, "while () {\n}")
          processor.registerUnresolvedError(whileStatement.getTextRange.getStartOffset + "while (".length)
        }
        else {
          processor.registerUnresolvedError(lParenth.getTextRange.getEndOffset)
        }
      }
      else if (rParenth == null) {
        doc.insertString(condition.getTextRange.getEndOffset, ")")
      }
    }
  }
}

