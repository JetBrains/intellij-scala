package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.openapi.editor.Editor
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScWhile}

/**
 * @author Dmitry.Naydanov
 * @author Ksenia.Sautina
 * @since 1/30/13
 */
@SuppressWarnings(Array("HardCodedStringLiteral"))
class ScalaWhileConditionFixer extends ScalaFixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement): OperationPerformed = {
    val whileStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScWhile], false)
    if (whileStatement == null) return NoOperation

    val doc = editor.getDocument
    val leftParenthesis = whileStatement.getLeftParenthesis.orNull
    val rightParenthesis = whileStatement.getRightParenthesis.orNull

    whileStatement.condition match {
      case None if leftParenthesis != null && !leftParenthesis.getNextSibling.isInstanceOf[PsiErrorElement] &&
        whileStatement.lastChild.exists(_.isInstanceOf[PsiErrorElement]) =>
        doc.insertString(whileStatement.lastChild.get.getTextRange.getEndOffset, ") {}")
        WithEnter(3)
      case None if leftParenthesis == null || rightParenthesis == null =>
        val whileStartOffset = whileStatement.getTextRange.getStartOffset
        var stopOffset = doc.getLineEndOffset(doc getLineNumber whileStartOffset)
        val whLength = "while (".length

        whileStatement.body.foreach(bl => stopOffset = Math.min(stopOffset, bl.getTextRange.getStartOffset))

        doc.replaceString(whileStartOffset, stopOffset, "while () {\n\n}")
        moveToStart(editor, whileStatement)

        WithReformat(whLength)
      case None =>
        moveToStart(editor, leftParenthesis)
        doc.insertString(rightParenthesis.getTextRange.getEndOffset, " {\n\n}")
        WithReformat(1)
      case Some(_) if rightParenthesis != null && whileStatement.body.isDefined =>
        whileStatement.body match {
          case Some(block: ScBlockExpr) =>
            return placeInWholeBlock(block, editor)
          case Some(expr) => moveToEnd(editor, expr)
          case _ =>
        }
        WithReformat(0)
      case Some(cond) if rightParenthesis == null =>
        doc.insertString(cond.getTextRange.getEndOffset, ")")
        WithReformat(0)
      case _ => NoOperation
    }
  }
}

