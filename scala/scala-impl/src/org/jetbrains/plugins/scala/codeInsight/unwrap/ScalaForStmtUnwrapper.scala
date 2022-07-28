package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFor

class ScalaForStmtUnwrapper extends ScalaUnwrapper {
  override def isApplicableTo(e: PsiElement): Boolean = e match {
    case fSt: ScFor => fSt.body.isDefined
    case _ => false
  }

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext): Unit = element match {
    case fSt: ScFor if fSt.body.isDefined =>
      context.extractBlockOrSingleStatement(fSt.body.get, fSt)
      context.delete(fSt)
    case _ =>
  }

  override def getDescription(e: PsiElement): String = CodeInsightBundle.message("unwrap.for")
}
