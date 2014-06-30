package org.jetbrains.plugins.scala
package codeInsight.unwrap

import java.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression, ScMethodCall}

import scala.annotation.tailrec

/**
 * Nikolay.Tropin
 * 2014-06-26
 */
class ScalaMethodCallArgUnwrapper extends ScalaUnwrapper with ShortTextDescription {

  override def getDescription(e: PsiElement) = super.getDescription(e)

  override def isApplicableTo(e: PsiElement) = forMethodCallArg(e)((_, _) => true)(false)

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext) = {
    forMethodCallArg(element) { (expr, call) =>
      context.extractBlockOrSingleStatement(expr, call)
      context.delete(call)
    } {}
  }

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]) = {
    forMethodCallArg[PsiElement](e) { (expr, call) =>
      super.collectAffectedElements(e, toExtract)
      call
    }(e)
  }

  private def forMethodCallArg[T](e: PsiElement)(ifArg: (ScExpression, ScMethodCall) => T)(ifNot: => T) = {
    e match {
      case (expr: ScExpression) childOf ((_: ScArgumentExprList) childOf (call: ScMethodCall)) =>
        @tailrec
        def maxCall(call: ScMethodCall): ScMethodCall = call.getParent match {
          case parCall: ScMethodCall => maxCall(parCall)
          case _ => call
        }
        ifArg(expr, maxCall(call))
      case _ => ifNot
    }
  }
}
