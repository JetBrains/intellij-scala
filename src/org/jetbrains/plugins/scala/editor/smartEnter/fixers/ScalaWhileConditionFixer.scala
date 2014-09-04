package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScWhileStmt

/**
 * @author Ksenia.Sautina
 * @since 1/30/13
 */

@SuppressWarnings(Array("HardCodedStringLiteral"))
class ScalaWhileConditionFixer extends ScalaFixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    val whileStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScWhileStmt], false)
    if (whileStatement == null) return
    val doc: Document = editor.getDocument
    val leftParenthesis = whileStatement.getLeftParenthesis.getOrElse(null)
    val rightParenthesis = whileStatement.getRightParenthesis.getOrElse(null)
    val condition = whileStatement.condition.getOrElse(null)
    if (condition == null) {
      if (leftParenthesis == null || rightParenthesis == null) {
        var stopOffset: Int = doc.getLineEndOffset(doc.getLineNumber(whileStatement.getTextRange.getStartOffset))
        val block = whileStatement.body.getOrElse(null)
        if (block != null) {
          stopOffset = Math.min(stopOffset, block.getTextRange.getStartOffset)
        }
        doc.replaceString(whileStatement.getTextRange.getStartOffset, stopOffset, "while () {\n}")
        processor.registerUnresolvedError(whileStatement.getTextRange.getStartOffset + "while (".length)
      }
      else {
        processor.registerUnresolvedError(leftParenthesis.getTextRange.getEndOffset)
      }
    } else if (rightParenthesis == null) {
      doc.insertString(condition.getTextRange.getEndOffset, ")")
    }
  }
}

