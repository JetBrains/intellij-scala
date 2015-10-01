package org.jetbrains.plugins.scala.extensions

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
 * Pavel Fatin
 */

object ExpressionType {
  def unapply(e: ScExpression): Option[ScType] = Option(e).flatMap(_.getType(TypingContext.empty).toOption)
}