package org.jetbrains.plugins.scala
package codeInsight.unwrap

import java.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTry

/**
 * Nikolay.Tropin
 * 2014-06-27
 */
class ScalaTryWithFinallyUnwrapper extends ScalaUnwrapper {
  override def isApplicableTo(e: PsiElement): Boolean = e.getParent match {
    case ScTry(tryBl, _, Some(finBl)) if finBl.expression.isDefined && (tryBl == e || finBl == e)  => true
    case _ => false
  }

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext): Unit = element.getParent match {
    case stmt @ ScTry(tryBl, _, Some(finBl)) if finBl.expression.isDefined =>
      context.extractBlockOrSingleStatement(tryBl, stmt)
      context.insertNewLine()
      context.extractBlockOrSingleStatement(finBl.expression.get, stmt)
      context.delete(stmt)
    case _ =>
  }

  override def getDescription(e: PsiElement): String = ScalaBundle.message("unwrap.try.with.finally")

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]): PsiElement = e.getParent match {
    case stmt: ScTry =>
      super.collectAffectedElements(e, toExtract)
      stmt
    case _ => e
  }
}
