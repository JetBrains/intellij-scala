package org.jetbrains.plugins.scala
package codeInsight.unwrap

import java.util

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScIfStmt

/**
 * Nikolay.Tropin
 * 2014-06-27
 */
class ScalaIfUnwrapper extends ScalaUnwrapper {
  override def isApplicableTo(e: PsiElement) = e.getParent match {
    case (_: ScIfStmt) childOf (_: ScIfStmt) => false
    case ScIfStmt(_, Some(`e`), _) => true
    case _ => false
  }

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext) = element.getParent match {
    case ifSt @ ScIfStmt(_, Some(thenBr), _) =>
      context.extractBlockOrSingleStatement(thenBr, ifSt)
      context.delete(ifSt)
    case _ =>
  }

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]) = e.getParent match {
    case ifSt @ ScIfStmt(_, Some(`e`), _) =>
      super.collectAffectedElements(e, toExtract)
      ifSt
    case _ => e
  }

  override def getDescription(e: PsiElement) = CodeInsightBundle.message("unwrap.if")
}
