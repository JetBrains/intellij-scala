package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

trait ScStableReferencePattern extends ScPattern {
  def reference: Option[ScReference] = findChild(classOf[ScReference])

  def referenceExpression: Option[ScReferenceExpression] = findChild(classOf[ScReferenceExpression])
}