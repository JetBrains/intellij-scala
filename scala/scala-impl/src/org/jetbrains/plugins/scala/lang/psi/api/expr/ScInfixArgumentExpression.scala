package org.jetbrains.plugins.scala
package lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api._


/**
 * @author Alefas
 * @since 25/03/14.
 */
trait ScInfixArgumentExpressionBase extends ScExpressionBase { this: ScInfixArgumentExpression =>
  /**
   * Return true if this expression is arguments for method invocation
   */
  def isCall: Boolean = getContext match {
    case infix: ScInfixExpr => infix.argsElement == this
    case _ => false
  }
}