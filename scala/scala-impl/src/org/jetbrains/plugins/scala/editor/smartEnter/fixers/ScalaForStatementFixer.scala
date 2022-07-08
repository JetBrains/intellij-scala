package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.openapi.editor.Editor
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScFor}

@SuppressWarnings(Array("HardCodedStringLiteral"))
class ScalaForStatementFixer extends ScalaFixer {
  override def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement): OperationPerformed = {
    val forStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScFor], false)
    if (forStatement == null) return NoOperation

    val doc = editor.getDocument
    val leftParenthesis = forStatement.getLeftParenthesis.orNull
    val rightParenthesis = forStatement.getRightParenthesis.orNull


    forStatement.enumerators match {
      case None if leftParenthesis == null && rightParenthesis == null =>
        val forStartOffset = forStatement.getTextRange.getStartOffset
        val stopOffset = doc.getLineEndOffset(doc.getLineNumber(forStartOffset))

        doc.replaceString(forStartOffset, stopOffset, "for () {\n}")
        editor.getCaretModel moveToOffset forStartOffset

        WithReformat(5)
      case None if leftParenthesis != null && rightParenthesis == null =>
        doc.insertString(forStatement.getTextRange.getEndOffset, ") {\n\n}")
        WithReformat(0)
      case None if leftParenthesis != null && rightParenthesis != null =>
        moveToStart(editor, rightParenthesis)
        doc.insertString(rightParenthesis.getTextRange.getEndOffset, " {\n\n}")
        WithReformat(0)
      case Some(cond) if rightParenthesis == null =>
        doc.insertString(cond.getTextRange.getEndOffset, ")")
        WithReformat(0)
      case Some(_) if rightParenthesis != null && forStatement.body.exists(_.isInstanceOf[ScBlockExpr]) =>
        placeInWholeBlock(forStatement.body.get.asInstanceOf[ScBlockExpr], editor)
      case _ => NoOperation
    }
  }
}

