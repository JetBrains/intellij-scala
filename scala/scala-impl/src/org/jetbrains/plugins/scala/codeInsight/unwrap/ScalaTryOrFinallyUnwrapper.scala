package org.jetbrains.plugins.scala
package codeInsight.unwrap

import java.util

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFinallyBlock, ScTryBlock, ScTry}

/**
 * Nikolay.Tropin
 * 2014-06-27
 */
class ScalaTryOrFinallyUnwrapper extends ScalaUnwrapper {
  override def isApplicableTo(e: PsiElement): Boolean = e match {
    case _: ScTryBlock => true
    case fBl: ScFinallyBlock if fBl.expression.isDefined => true
    case _ => false
  }

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext): Unit = element.getParent match {
    case stmt @ ScTry(tryBlock, _, _) if tryBlock == element =>
      context.extractBlockOrSingleStatement(tryBlock, stmt)
      context.delete(stmt)
    case stmt @ ScTry(_, _, Some(fBl)) if fBl == element && fBl.expression.isDefined =>
      context.extractBlockOrSingleStatement(fBl.expression.get, stmt)
      context.delete(stmt)
    case _ =>
  }

  override def getDescription(e: PsiElement): String = e match {
    case _: ScTryBlock => CodeInsightBundle.message("unwrap.try")
    case _: ScFinallyBlock => ScalaBundle.message("unwrap.finally")
    case _ => ""
  }

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]): PsiElement = e.getParent match {
    case _: ScTry =>
      super.collectAffectedElements(e, toExtract)
      e
    case _ => e
  }
}
