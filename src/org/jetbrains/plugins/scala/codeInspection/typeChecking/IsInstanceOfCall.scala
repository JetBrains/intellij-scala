package org.jetbrains.plugins.scala.codeInspection.typeChecking

import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScGenericCall, ScParenthesisedExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction

/**
 * @author Nikolay.Tropin
 */
object IsInstanceOfCall {
  def unapply(expression: ScExpression): Option[ScGenericCall] = {
    expression match {
      case ScParenthesisedExpr(IsInstanceOfCall(call)) => Some(call)
      case call: ScGenericCall =>
        call.referencedExpr match {
          case ref: ScReferenceExpression if ref.refName == "isInstanceOf" =>
            ref.resolve() match {
              case synth: ScSyntheticFunction => Some(call)
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }
}