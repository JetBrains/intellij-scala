package org.jetbrains.plugins.scala
package lang.psi.api.base

import lang.resolve.ResolvableReferenceElement

/**
 * User: Dmitry Naydanov
 * Date: 3/17/12
 */

trait ScInterpolatedStringLiteral extends ScLiteral {
  object InterpolatedStringType extends Enumeration {
    type StringType = Value
    val STANDART, FORMAT, PATTERN = Value
  }

  def getType: InterpolatedStringType.StringType
}
