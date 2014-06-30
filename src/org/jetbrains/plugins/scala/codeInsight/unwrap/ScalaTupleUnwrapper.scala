package org.jetbrains.plugins.scala
package codeInsight.unwrap

import java.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScTuple}

/**
 * Nikolay.Tropin
 * 2014-06-26
 */
class ScalaTupleUnwrapper extends ScalaUnwrapper with ShortTextDescription {
  override def isApplicableTo(e: PsiElement) = forTupledExpression(e)((_, _) => true)(false)

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext) = {
    forTupledExpression(element){ (expr, tuple) =>
      context.extractElement(expr, tuple)
      context.delete(tuple)
    } ()
  }


  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]) = {
    forTupledExpression[PsiElement](e){ (expr, tuple) =>
      super.collectAffectedElements(e, toExtract)
      tuple
    } (e)
  }

  private def forTupledExpression[T](e: PsiElement)(ifTupled: (ScExpression, ScTuple) => T)(ifNot: => T) = {
    e match {
      case (expr: ScExpression) childOf (tuple: ScTuple) => ifTupled(expr, tuple)
      case _ => ifNot
    }
  }
}
