package org.jetbrains.plugins.scala.extensions

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * Pavel Fatin
 */

object ExpressionType {
  def unapply(e: ScExpression): Option[ScType] = e.getType(TypingContext.empty).toOption
}