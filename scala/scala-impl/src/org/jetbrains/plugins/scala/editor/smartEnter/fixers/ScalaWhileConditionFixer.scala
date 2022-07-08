package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.openapi.editor.Editor
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScWhile}

@SuppressWarnings(Array("HardCodedStringLiteral"))
class ScalaWhileConditionFixer extends ScalaFixer {
  override def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement): OperationPerformed = {
    val whileStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScWhile], false)
    if (whileStatement == null) return NoOperation

    val doc = editor.getDocument
    val leftParenthesis = whileStatement.leftParen.orNull
    val rightParenthesis = whileStatement.rightParen.orNull

    whileStatement.condition match {
      case None if leftParenthesis != null && !leftParenthesis.getNextSibling.isInstanceOf[PsiErrorElement] &&
        whileStatement.lastChild.exists(_.isInstanceOf[PsiErrorElement]) =>
        doc.insertString(whileStatement.lastChild.get.getTextRange.getEndOffset, ") {}")
        WithEnter(3)
      case None if leftParenthesis == null || rightParenthesis == null =>
        val whileStartOffset = whileStatement.getTextRange.getStartOffset
        var stopOffset = doc.getLineEndOffset(doc getLineNumber whileStartOffset)
        val whLength = "while (".length

        whileStatement.expression.foreach(bl => stopOffset = Math.min(stopOffset, bl.getTextRange.getStartOffset))

        doc.replaceString(whileStartOffset, stopOffset, "while () {\n\n}")
        moveToStart(editor, whileStatement)

        WithReformat(whLength)
      case None =>
        moveToStart(editor, leftParenthesis)
        doc.insertString(rightParenthesis.getTextRange.getEndOffset, " {\n\n}")
        WithReformat(1)
      case Some(_) if rightParenthesis != null && whileStatement.expression.isDefined =>
        whileStatement.expression match {
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

