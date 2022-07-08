package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

trait ScPostfixExpr extends ScExpression with ScSugarCallExpr {

  def operand: ScExpression = findChild[ScExpression].get

  override def operation: ScReferenceExpression = findLastChild[ScExpression].get match {
    case re: ScReferenceExpression => re
    case _ =>
      throw new UnsupportedOperationException("Postfix Expr Operation is not reference expression: " + this.getText)
  }

  override def getBaseExpr: ScExpression = operand

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitPostfixExpression(this)
  }
}

object ScPostfixExpr {
  def unapply(e: ScPostfixExpr): Some[(ScExpression, ScReferenceExpression)] = Some(e.operand, e.operation)
}