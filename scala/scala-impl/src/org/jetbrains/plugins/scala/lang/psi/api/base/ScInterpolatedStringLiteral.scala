package org.jetbrains.plugins.scala
package lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

/**
 * User: Dmitry Naydanov
 * Date: 3/17/12
 */

trait ScInterpolatedStringLiteral extends ScLiteral with ScInterpolated {
  def getType: InterpolatedStringType.StringType
  def reference: Option[ScReferenceExpression]
}

object InterpolatedStringType extends Enumeration {
  type StringType = Value
  val STANDART, FORMAT, PATTERN, RAW = Value
}