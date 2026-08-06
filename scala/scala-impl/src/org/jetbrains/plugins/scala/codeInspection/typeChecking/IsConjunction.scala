package org.jetbrains.plugins.scala.codeInspection.typeChecking

import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr}

object IsConjunction {
  def unapply(expression: ScInfixExpr): Option[(ScExpression, ScExpression)] = expression match {
    case ScInfixExpr(left, operation, right) if operation.refName == "&&" => Some(left, right)
    case _ => None
  }
}
