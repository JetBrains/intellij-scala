package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScParenthesizedElement

/**
  * @author Alexander Podkhalyuzin
  *         Date: 07.03.2008
  */
trait ScParenthesisedExpr extends ScInfixArgumentExpression with ScParenthesizedElement {
  type Kind = ScExpression

  override def innerElement: Option[ScExpression] = findChild(classOf[ScExpression])

  override def sameTreeParent: Option[ScExpression] = getParent.asOptionOf[ScExpression]

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitExprInParent(this)
  }
}

object ScParenthesisedExpr {
  def unapply(p: ScParenthesisedExpr): Option[ScExpression] = p.innerElement
}