package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScTuple}

import java.util

class ScalaTupleUnwrapper extends ScalaUnwrapper with ShortTextDescription {
  override def isApplicableTo(e: PsiElement): Boolean = forTupledExpression(e)((_, _) => true)(false)

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext): Unit = {
    forTupledExpression(element){ (expr, tuple) =>
      context.extractElement(expr, tuple)
      context.delete(tuple)
    } {}
  }


  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]): PsiElement = {
    forTupledExpression[PsiElement](e){ (_, tuple) =>
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
