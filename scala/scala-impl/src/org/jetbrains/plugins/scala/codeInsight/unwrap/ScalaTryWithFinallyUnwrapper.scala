package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFinallyBlock, ScTry}

import java.util

class ScalaTryWithFinallyUnwrapper extends ScalaUnwrapper {
  override def isApplicableTo(e: PsiElement): Boolean = e.getParent match {
    case ScTry(Some(tryBl), _, Some(finBl@ScFinallyBlock(_))) if tryBl == e || finBl == e  => true
    case _ => false
  }

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext): Unit = element.getParent match {
    case stmt @ ScTry(Some(tryBl), _, Some(ScFinallyBlock(fExpr))) =>
      context.extractBlockOrSingleStatement(tryBl, stmt)
      context.insertNewLine()
      context.extractBlockOrSingleStatement(fExpr, stmt)
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
