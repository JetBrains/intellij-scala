package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement

import scala.annotation.tailrec

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
trait ScMethodCall extends ScExpression with MethodInvocation {

  @tailrec
  final def deepestInvokedExpr: ScExpression =
    getEffectiveInvokedExpr match {
      case call: ScMethodCall => call.deepestInvokedExpr
      case expr               => expr
    }

  final def argumentListCount: Int = getEffectiveInvokedExpr match {
    case call: ScMethodCall => call.argumentListCount + 1
    case _ => 1
  }

  def args: ScArgumentExprList = findChildByClassScala(classOf[ScArgumentExprList])

  override def isUpdateCall: Boolean = getContext.isInstanceOf[ScAssignment] &&
    getContext.asInstanceOf[ScAssignment].leftExpression == this

  def updateExpression(): Option[ScExpression] = {
    getContext match {
      case a: ScAssignment if a.leftExpression == this => a.rightExpression
      case _ => None
    }
  }

  override def argsElement: PsiElement = args

  /**
    * If named parameters enabled for this method even if it is from java; needed for Play 2 support
    */
  def isNamedParametersEnabledEverywhere: Boolean = false

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitMethodCallExpression(this)
  }
}

object ScMethodCall {
  def unapply(call: ScMethodCall): Option[(ScExpression, collection.Seq[ScExpression])] =
    Some(call.getInvokedExpr, call.argumentExpressions)

  object withDeepestInvoked {

    def unapply(call: ScMethodCall): Option[ScExpression] =
      Option(call.deepestInvokedExpr)
  }
}