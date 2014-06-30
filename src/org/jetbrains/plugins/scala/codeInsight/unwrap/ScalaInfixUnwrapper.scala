package org.jetbrains.plugins.scala
package codeInsight.unwrap

import java.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr

/**
 * Nikolay.Tropin
 * 2014-06-26
 */
class ScalaInfixUnwrapper extends ScalaUnwrapper with ShortTextDescription {
  override def isApplicableTo(e: PsiElement) = e.getParent match {
    case ScInfixExpr(left, _, right) => e == left || e == right
    case _ => false
  }

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext) = {
    element.getParent match {
      case infix: ScInfixExpr =>
        context.extractElement(element, infix)
        context.delete(infix)
      case _ =>
    }
  }

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]) = {
    super.collectAffectedElements(e, toExtract)
    e.getParent
  }

}
