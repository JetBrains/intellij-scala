package org.jetbrains.plugins.scala.lang.psi.api.expr

/**
 * Generic interface for sugar calls like
 * Infix, Prefix and Postfix calls
 */
trait ScSugarCallExpr extends ScExpression with MethodInvocation {
  override def thisExpr: Option[ScExpression] = Some(getBaseExpr)

  def getBaseExpr: ScExpression

  def operation: ScReferenceExpression
}

object ScSugarCallExpr {
  def unapply(sugarCall: ScSugarCallExpr): Option[(ScExpression, ScReferenceExpression, Seq[ScExpression])] =
    Some(sugarCall.getBaseExpr, sugarCall.operation, sugarCall.argumentExpressions)
}