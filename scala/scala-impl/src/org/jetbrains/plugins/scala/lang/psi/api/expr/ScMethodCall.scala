package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor

import scala.annotation.tailrec

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

  def args: ScArgumentExprList = findChild[ScArgumentExprList].get

  override def isUpdateCall: Boolean = getContext match {
    case ScAssignment(lhs, _) => lhs == this
    case _                    => false
  }

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
  def unapply(call: ScMethodCall): Option[(ScExpression, Seq[ScExpression])] =
    Some(call.getInvokedExpr, call.argumentExpressions)

  object withDeepestInvoked {

    def unapply(call: ScMethodCall): Option[ScExpression] =
      Option(call.deepestInvokedExpr)
  }
}