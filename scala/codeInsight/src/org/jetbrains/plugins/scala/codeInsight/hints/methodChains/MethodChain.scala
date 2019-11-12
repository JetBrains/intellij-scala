package org.jetbrains.plugins.scala.codeInsight.hints.methodChains

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScMethodCall, ScParenthesisedExpr, ScReferenceExpression}

import scala.annotation.tailrec


private object MethodChain {
  def unapply(element: PsiElement): Option[Seq[ScExpression]] = {
    element match {
      case expr: ScExpression if isMostOuterExpression(expr) =>
        Some(collectChain(expr))
      case _ => None
    }
  }

  private def isMostOuterExpression(expr: PsiElement): Boolean = {
    expr.getParent match {
      case _: ScReferenceExpression | _: ScMethodCall | _: ScParenthesisedExpr => false
      case _ => true
    }
  }

  private def collectChain(expr: ScExpression): List[ScExpression] = {
    @tailrec
    def collectChainAcc(expr: ScExpression, acc: List[ScExpression]): List[ScExpression] = {
      val newAcc = if (!expr.parent.exists(_.is[ScMethodCall])) expr :: acc else acc
      expr match {
        case ChainCall(inner) => collectChainAcc(inner, newAcc)
        case _ => newAcc
      }
    }
    collectChainAcc(expr, Nil)
  }

  private object ChainCall {
    def unapply(element: PsiElement): Option[ScExpression] = element match {
      case ScReferenceExpression.withQualifier(inner) => Some(inner)
      case invoc: MethodInvocation => Some(invoc.getEffectiveInvokedExpr)
      case ScParenthesisedExpr(inner) => Some(inner)
      case _ => None
    }
  }
}