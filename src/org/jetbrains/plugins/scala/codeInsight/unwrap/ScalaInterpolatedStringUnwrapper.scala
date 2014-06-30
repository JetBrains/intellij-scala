package org.jetbrains.plugins.scala
package codeInsight.unwrap

import java.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Nikolay.Tropin
 * 2014-06-30
 */
class ScalaInterpolatedStringUnwrapper extends ScalaUnwrapper {
  
  override def isApplicableTo(e: PsiElement) = forInjection(e)((_, _) => true)(false)

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext) = forInjection(element){
    (expr, lit) =>
      context.extractBlockOrSingleStatement(expr, lit)
      context.delete(lit)
  } ()
  
  override def getDescription(e: PsiElement) = ScalaBundle.message("unwrap.interpolated.string.injection")

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]) = forInjection[PsiElement](e) {
    (expr, lit) =>
      super.collectAffectedElements(expr, toExtract)
      lit
  } (e)

  private def forInjection[T](e: PsiElement)
                             (ifInjection: (ScExpression, ScInterpolatedStringLiteral) => T)
                             (ifNot: => T): T = {
    e match {
      case (expr: ScExpression) childOf (lit: ScInterpolatedStringLiteral) => ifInjection(expr, lit)
      case _ => ifNot
    }
  }
  
}
