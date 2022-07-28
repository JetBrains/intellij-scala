package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import java.util

class ScalaInterpolatedStringUnwrapper extends ScalaUnwrapper {
  
  override def isApplicableTo(e: PsiElement): Boolean = forInjection(e)((_, _) => true)(false)

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext): Unit = forInjection(element){
    (expr, lit) =>
      context.extractBlockOrSingleStatement(expr, lit)
      context.delete(lit)
  } {}
  
  override def getDescription(e: PsiElement): String = ScalaBundle.message("unwrap.interpolated.string.injection")

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]): PsiElement = forInjection[PsiElement](e) {
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
