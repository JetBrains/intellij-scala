package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScParenthesizedElement

trait ScParenthesisedExpr extends ScInfixArgumentExpression with ScParenthesizedElement {
  type Kind = ScExpression

  override def innerElement: Option[ScExpression] = findChild[ScExpression]

  override def sameTreeParent: Option[ScExpression] = getParent.asOptionOf[ScExpression]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitParenthesisedExpr(this)
  }
}

object ScParenthesisedExpr {
  def unapply(p: ScParenthesisedExpr): Option[ScExpression] = p.innerElement
}