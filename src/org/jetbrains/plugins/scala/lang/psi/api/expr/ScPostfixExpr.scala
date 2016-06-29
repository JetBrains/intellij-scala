package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScPostfixExpr extends ScExpression with ScSugarCallExpr {
  def operand: ScExpression = findChildrenByClassScala(classOf[ScExpression]).apply(0)
  def operation : ScReferenceExpression = findChildrenByClassScala(classOf[ScExpression]).apply(1) match {
    case re : ScReferenceExpression => re
    case _ =>
      throw new UnsupportedOperationException("Postfix Expr Operation is not reference expression: " + this.getText)
  }

  def getBaseExpr: ScExpression = operand
}

object ScPostfixExpr {
  def unapply(e: ScPostfixExpr) = Some(e.operand, e.operation)
}