package org.jetbrains.plugins.scala.codeInspection.quickfix

import com.intellij.modcommand.{ActionContext, ModCommand, ModPsiUpdater, PsiBasedModCommandAction}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

final class ConvertFromInfixExpressionQuickFix(expr: ScInfixExpr)
  extends PsiBasedModCommandAction[ScInfixExpr](expr)
    with DumbAware {
  override def getFamilyName: String = ConvertFromInfixExpressionQuickFix.message

  override def perform(context: ActionContext, expr: ScInfixExpr): ModCommand = {
    ModCommand.psiUpdate(expr, (expr: ScInfixExpr, updater: ModPsiUpdater) => {
      val start = expr.getTextRange.getStartOffset
      val diff = context.offset() - expr.operation.nameId.getTextRange.getStartOffset

      val methodCallExpr = ScalaPsiElementFactory.createEquivMethodCall(expr)
      val referenceExpr = methodCallExpr.getInvokedExpr match {
        case ref: ScReferenceExpression => ref
        case ScGenericCall(ref, _) => ref
      }
      val size = referenceExpr.nameId.getTextRange.getStartOffset -
        methodCallExpr.getTextRange.getStartOffset

      expr.replaceExpression(methodCallExpr, removeParenthesis = true)
      updater.moveCaretTo(start + diff + size)
    })
  }
}

object ConvertFromInfixExpressionQuickFix {
  val message: String = ScalaInspectionBundle.message("convert.from.infix.expression")
}
