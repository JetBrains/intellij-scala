package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr

import java.util

class ScalaInfixUnwrapper extends ScalaUnwrapper with ShortTextDescription {
  override def isApplicableTo(e: PsiElement): Boolean = e.getParent match {
    case ScInfixExpr(left, _, right) => e == left || e == right
    case _ => false
  }

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext): Unit = {
    element.getParent match {
      case infix: ScInfixExpr =>
        context.extractElement(element, infix)
        context.delete(infix)
      case _ =>
    }
  }

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]): PsiElement = {
    super.collectAffectedElements(e, toExtract)
    e.getParent
  }

}
