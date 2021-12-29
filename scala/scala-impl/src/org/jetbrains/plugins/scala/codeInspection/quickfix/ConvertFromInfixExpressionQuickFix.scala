package org.jetbrains.plugins.scala.codeInspection.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.codeInspection.quickfix.ConvertFromInfixExpressionQuickFix.message
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

final class ConvertFromInfixExpressionQuickFix(expr: ScInfixExpr) extends AbstractFixOnPsiElement(message, expr) {
  override protected def doApplyFix(infixExpr: ScInfixExpr)(implicit project: Project): Unit = {
    val editor = infixExpr.openTextEditor.getOrElse(return)
    ConvertFromInfixExpressionQuickFix.applyFix(infixExpr, editor)
  }
}

object ConvertFromInfixExpressionQuickFix {
  val message: String = ScalaInspectionBundle.message("convert.from.infix.expression")

  def applyFix(infixExpr: ScInfixExpr, editor: Editor)(implicit project: Project): Unit = {
    val start = infixExpr.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - infixExpr.operation.nameId.getTextRange.getStartOffset

    val methodCallExpr = ScalaPsiElementFactory.createEquivMethodCall(infixExpr)
    val referenceExpr = methodCallExpr.getInvokedExpr match {
      case ref: ScReferenceExpression => ref
      case ScGenericCall(ref, _) => ref
    }
    val size = referenceExpr.nameId.getTextRange.getStartOffset -
      methodCallExpr.getTextRange.getStartOffset

    inWriteAction {
      infixExpr.replaceExpression(methodCallExpr, removeParenthesis = true)
      editor.getCaretModel.moveToOffset(start + diff + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }
}
