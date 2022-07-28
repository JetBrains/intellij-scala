package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScCatchBlock, ScFinallyBlock}

class ScalaCatchOrFinallyRemover extends ScalaUnwrapper {
  override def isApplicableTo(e: PsiElement): Boolean = e match {
    case _: ScFinallyBlock | _: ScCatchBlock => true
    case _ => false
  }

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext): Unit = element match {
    case fBl: ScFinallyBlock => context.delete(fBl)
    case cBl: ScCatchBlock => context.delete(cBl)
    case _ =>
  }

  override def getDescription(e: PsiElement): String = e match {
    case _: ScFinallyBlock => ScalaBundle.message("remove.finally")
    case _: ScCatchBlock => ScalaBundle.message("remove.catch")
    case _ => ""
  }
}
