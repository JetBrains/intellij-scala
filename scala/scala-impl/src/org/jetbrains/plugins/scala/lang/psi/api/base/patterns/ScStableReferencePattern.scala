package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

trait ScStableReferencePattern extends ScPattern {
  def referenceExpression: Option[ScReferenceExpression]
}

object ScStableReferencePattern {

  def unapply(pattern: ScStableReferencePattern): Option[ScReferenceExpression] = pattern.referenceExpression
}