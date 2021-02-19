package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScParenthesizedElement, ScParenthesizedElementBase}

/**
  * @author Alexander Podkhalyuzin
  *         Date: 07.03.2008
  */
trait ScParenthesisedExprBase extends ScInfixArgumentExpressionBase with ScParenthesizedElementBase { this: ScParenthesisedExpr =>
  type Kind = ScExpression

  override def innerElement: Option[ScExpression] = findChild[ScExpression]

  override def sameTreeParent: Option[ScExpression] = getParent.asOptionOf[ScExpression]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitParenthesisedExpr(this)
  }
}

abstract class ScParenthesisedExprCompanion {
  def unapply(p: ScParenthesisedExpr): Option[ScExpression] = p.innerElement
}