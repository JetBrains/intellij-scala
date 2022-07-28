package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFinallyBlock, ScTry}

import java.util

class ScalaTryOrFinallyUnwrapper extends ScalaUnwrapper {
  override def isApplicableTo(e: PsiElement): Boolean = e match {
    case _: ScTry => true
    case fBl: ScFinallyBlock if fBl.expression.isDefined => true
    case _ => false
  }

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext): Unit = element.getParent match {
    case stmt @ ScTry(Some(tryExpr), _, _) if tryExpr == element =>
      context.extractBlockOrSingleStatement(tryExpr, stmt)
      context.delete(stmt)
    case stmt @ ScTry(_, _, Some(fBl@ScFinallyBlock(fExpr))) if fBl == element =>
      context.extractBlockOrSingleStatement(fExpr, stmt)
      context.delete(stmt)
    case _ =>
  }

  override def getDescription(e: PsiElement): String = e match {
    case _: ScTry => CodeInsightBundle.message("unwrap.try")
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
