package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScForStatement

/**
 * Nikolay.Tropin
 * 2014-06-27
 */
class ScalaForStmtUnwrapper extends ScalaUnwrapper {
  override def isApplicableTo(e: PsiElement) = e match {
    case fSt: ScForStatement => fSt.body.isDefined
    case _ => false
  }

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext) = element match {
    case fSt: ScForStatement if fSt.body.isDefined =>
      context.extractBlockOrSingleStatement(fSt.body.get, fSt)
      context.delete(fSt)
    case _ =>
  }

  override def getDescription(e: PsiElement) = CodeInsightBundle.message("unwrap.for")
}
