package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.PsiElement

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
trait ScPrefixExprBase extends ScExpressionBase with ScSugarCallExprBase { this: ScPrefixExpr =>
  def operand: ScExpression = findLastChild[ScExpression].get

  override def operation: ScReferenceExpression = findChild[ScExpression].get match {
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

abstract class ScPrefixExprCompanion {
  def unapply(e: ScPrefixExpr): Some[(ScReferenceExpression, ScExpression)] = Some(e.operation, e.operand)
}