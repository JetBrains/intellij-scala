package org.jetbrains.plugins.scala.codeInspection.expression

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, ReplaceQuickFix}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScIntLiteral, ScLiteral, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScParenthesisedExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.types.StdType

class ConstantExpressionInspection extends AbstractInspection {
  def actionFor(holder: ProblemsHolder) = {
    case ExpressionType(t) if t != StdType.INT => // do nothing
    case _: ScLiteral | _: ScReferenceElement |
         ScParenthesisedExpr(_: ScLiteral | _: ScReferenceElement) |
         Parent(_: ScInfixExpr | _: ScParenthesisedExpr) => // do nothing
    case element @ ConstantExpression(x) =>
      holder.registerProblem(element, s"Can be simplified to '$x'",
        new ReplaceQuickFix("scala", s"Replace with '$x'", x.toString))
  }
}

object ConstantExpression {
  private val Functions = Map[String, (Int, Int) => Int](
    ("+", _ + _), ("-", _ - _), ("*", _ * _), ("/", _ / _))

  def unapply(e: PsiElement): Option[Int] = Some(e) collect {
    case ScIntLiteral(x) => x
    case ScParenthesisedExpr(ConstantExpression(x)) => x
    case ScInfixExpr(ConstantExpression(l), ElementText(s), ConstantExpression(r))
      if Functions.contains(s) => Functions(s)(l, r)
    case ScReferenceElement(Parent(Parent(
      ScPatternDefinition.expr(ConstantExpression(x))))) => x
  }
}
