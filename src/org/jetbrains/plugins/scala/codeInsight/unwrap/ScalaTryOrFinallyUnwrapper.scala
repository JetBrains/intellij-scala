package org.jetbrains.plugins.scala
package codeInsight.unwrap

import java.util

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFinallyBlock, ScTryBlock, ScTryStmt}

/**
 * Nikolay.Tropin
 * 2014-06-27
 */
class ScalaTryOrFinallyUnwrapper extends ScalaUnwrapper {
  override def isApplicableTo(e: PsiElement) = e match {
    case _: ScTryBlock => true
    case fBl: ScFinallyBlock if fBl.expression.isDefined => true
    case _ => false
  }

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext) = element.getParent match {
    case stmt @ ScTryStmt(tryBlock, _, _) if tryBlock == element =>
      context.extractBlockOrSingleStatement(tryBlock, stmt)
      context.delete(stmt)
    case stmt @ ScTryStmt(_, _, Some(fBl)) if fBl == element && fBl.expression.isDefined =>
      context.extractBlockOrSingleStatement(fBl.expression.get, stmt)
      context.delete(stmt)
    case _ =>
  }

  override def getDescription(e: PsiElement) = e match {
    case _: ScTryBlock => CodeInsightBundle.message("unwrap.try")
    case _: ScFinallyBlock => ScalaBundle.message("unwrap.finally")
    case _ => ""
  }

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]) = e.getParent match {
    case _: ScTryStmt =>
      super.collectAffectedElements(e, toExtract)
      e
    case _ => e
  }
}
