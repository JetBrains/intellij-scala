package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
trait ScPrefixExpr extends ScExpression with ScSugarCallExpr {
  def operand: ScExpression = findChildrenByClassScala(classOf[ScExpression]).apply(1)

  override def operation: ScReferenceExpression = findChildrenByClassScala(classOf[ScExpression]).apply(0) match {
    case re: ScReferenceExpression => re
    case _ =>
      throw new UnsupportedOperationException("Prefix Expr Operation is not reference expression: " + this.getText)
  }

  override def argsElement: PsiElement = operation

  override def getBaseExpr: ScExpression = operand

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitPrefixExpression(this)
  }
}

object ScPrefixExpr {
  def unapply(e: ScPrefixExpr): Some[(ScReferenceExpression, ScExpression)] = Some(e.operation, e.operand)
}