package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScMethodCall extends ScExpression with MethodInvocation {
  def deepestInvokedExpr: ScExpression = {
    getEffectiveInvokedExpr match {
      case call: ScMethodCall => {
        call.deepestInvokedExpr
      }
      case expr => expr
    }
  }

  def args: ScArgumentExprList = findChildByClassScala(classOf[ScArgumentExprList])

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitMethodCallExpression(this)
  }

  override def isUpdateCall: Boolean = getContext.isInstanceOf[ScAssignStmt] &&
                      getContext.asInstanceOf[ScAssignStmt].getLExpression == this

  def updateExpression(): Option[ScExpression] = {
    getContext match {
      case a: ScAssignStmt if a.getLExpression == this => a.getRExpression
      case _ => None
    }
  }

  def argsElement: PsiElement = args
}

object ScMethodCall {
  def unapply(call: ScMethodCall) =
    Some(call.getInvokedExpr, call.argumentExpressions)
}