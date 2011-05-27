package org.jetbrains.plugins.scala.lang.psi.api.expr

/**
 * Generic interface for sugar calls like
 * Infix, Prefix and Postfix calls
 */
trait ScSugarCallExpr extends ScExpression {
  def operation: ScReferenceExpression
}