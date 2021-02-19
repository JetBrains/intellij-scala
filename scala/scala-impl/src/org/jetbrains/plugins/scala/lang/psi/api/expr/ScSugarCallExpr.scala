package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api._


/**
 * Generic interface for sugar calls like
 * Infix, Prefix and Postfix calls
 */
trait ScSugarCallExprBase extends ScExpressionBase with MethodInvocationBase { this: ScSugarCallExpr =>
  def getBaseExpr: ScExpression

  def operation: ScReferenceExpression
}

abstract class ScSugarCallExprCompanion {
  def unapply(sugarCall: ScSugarCallExpr): Option[(ScExpression, ScReferenceExpression, Seq[ScExpression])] =
    Some(sugarCall.getBaseExpr, sugarCall.operation, sugarCall.argumentExpressions)
}