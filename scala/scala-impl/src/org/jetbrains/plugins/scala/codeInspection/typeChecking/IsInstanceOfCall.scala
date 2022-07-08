package org.jetbrains.plugins.scala.codeInspection.typeChecking

import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScGenericCall, ScParenthesisedExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction

object IsInstanceOfCall {
  def unapply(expression: ScExpression): Option[ScGenericCall] = {
    expression match {
      case ScParenthesisedExpr(IsInstanceOfCall(call)) => Some(call)
      case call: ScGenericCall =>
        call.referencedExpr match {
          case ref: ScReferenceExpression if ref.refName == "isInstanceOf" =>
            ref.resolve() match {
              case _: ScSyntheticFunction => Some(call)
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }

  object withoutExplicitType {
    def unapply(expression: ScExpression): Boolean = expression match {
      case ref: ScReferenceExpression if ref.refName == "isInstanceOf" =>
        ref.resolve() match {
          case _: ScSyntheticFunction =>
            ref.getParent match {
              case _: ScGenericCall => false
              case _ => true
            }
          case null => true
          case _ => false
        }
      case _ => false
    }
  }
}
