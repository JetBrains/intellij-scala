package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api.base.{ScGenericParenthesisedNode, TreeMember}

/**
  * @author Alexander Podkhalyuzin
  *         Date: 07.03.2008
  */
trait ScParenthesisedExpr extends ScInfixArgumentExpression with ScGenericParenthesisedNode[ScExpression] {

  def expr: Option[ScExpression] = findChild(classOf[ScExpression])

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitExprInParent(this)
  }

  override def subNode: Option[ScExpression] = expr

  // This shouldn't be used, the algorithm for calculating isParenthesisNeeded is overridden
  override protected def getPrecedence(e: TreeMember[ScExpression]): Int = throw new IllegalAccessException

  override def isParenthesisNeeded: Boolean = ScalaPsiUtil.needParentheses(this, expr.get)

  override def isParenthesisClarifying: Boolean
  = (getParent, expr.get) match {
    case (_: ScSugarCallExpr, _: ScSugarCallExpr) => true
    case _ => false
  }
}

object ScParenthesisedExpr {
  def unapply(p: ScParenthesisedExpr): Option[ScExpression] = p.expr
}