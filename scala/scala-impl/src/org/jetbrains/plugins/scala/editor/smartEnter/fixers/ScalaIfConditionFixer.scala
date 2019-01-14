package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.openapi.editor.Editor
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScIf

/**
 * @author Dmitry.Naydanov
 * @author Ksenia.Sautina
 * @since 1/28/13
 */
@SuppressWarnings(Array("HardCodedStringLiteral"))
class ScalaIfConditionFixer extends ScalaFixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement): OperationPerformed = {
    val ifStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScIf], false)
    if (ifStatement == null) return NoOperation

    val doc = editor.getDocument
    val leftParenthesis = ifStatement.getLeftParenthesis.orNull
    val rightParenthesis = ifStatement.getRightParenthesis.orNull

    ifStatement.condition match {
      case None if leftParenthesis == null && rightParenthesis == null =>
        val ifStartOffset = ifStatement.getTextRange.getStartOffset
        var stopOffset = doc.getLineEndOffset(doc getLineNumber ifStartOffset)

        ifStatement.thenBranch.foreach {
          case thenBranch => stopOffset = Math.min(stopOffset, thenBranch.getTextRange.getStartOffset)
        }

        doc.replaceString(ifStartOffset, stopOffset, "if () {\n\n}")

        editor.getCaretModel.moveToOffset(ifStartOffset)

        WithReformat(4)
      case None if leftParenthesis != null && rightParenthesis == null =>
        def calcOffset(): Int = {
          var s = leftParenthesis.getNextSibling

          while (s != null) {
            s match {
              case error: PsiErrorElement => return error.getTextRange.getEndOffset
              case sp: PsiWhiteSpace if sp.textContains('\n') => return sp.getTextRange.getStartOffset
              case _ =>
            }

            s = s.getNextSibling
          }

          ifStatement.getTextRange.getEndOffset
        }

        val actualOffset = calcOffset()
        doc.insertString(actualOffset, ") {\n\n}")
        WithReformat(0)
      case None if leftParenthesis != null && rightParenthesis != null =>
        moveToStart(editor, rightParenthesis)
        doc.insertString(rightParenthesis.getTextRange.getEndOffset, " {\n\n}")
        WithReformat(0)
      case Some(cond) if rightParenthesis == null =>
        doc.insertString(cond.getTextRange.getEndOffset, ")")
        WithReformat(0)
      case _ => NoOperation
    }
  }
}

