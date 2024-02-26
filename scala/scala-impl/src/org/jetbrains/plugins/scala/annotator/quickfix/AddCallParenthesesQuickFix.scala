package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScGenericCall, ScPostfixExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class AddCallParenthesesQuickFix(expression: ScExpression)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("add.call.parentheses"), expression)
{
  override protected def doApplyFix(expression: ScExpression)(implicit project: Project): Unit = {
    val target = expression.getParent match {
      case postfix: ScPostfixExpr => postfix
      case call: ScGenericCall => call
      case _ => expression
    }

    val replacement = ScalaPsiElementFactory.createExpressionFromText(s"${target.getText}()", expression)
    target.replace(replacement)
  }
}
