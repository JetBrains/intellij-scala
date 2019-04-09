package org.jetbrains.plugins.scala
package codeInspection
package typeChecking

object IsConjunction {

  import lang.psi.api.expr.{ScExpression, ScInfixExpr}

  def unapply(expression: ScInfixExpr): Option[(ScExpression, ScExpression)] = expression match {
    case ScInfixExpr(left, operation, right) if operation.refName == "&&" => Some(left, right)
    case _ => None
  }
}
