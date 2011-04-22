package org.jetbrains.plugins.scala.lang.psi.api.expr

/**
 * Pavel Fatin
 */

// A common trait for ScInfixExpr and ScMethodCall to handle them uniformly
trait MethodInvocation {
  def getInvokedExpr: ScExpression

  def argumentExpressions: Seq[ScExpression]
}