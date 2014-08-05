package org.jetbrains.plugins.scala
package codeInsight.unwrap

import java.util

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScDoStmt, ScWhileStmt}

/**
 * Nikolay.Tropin
 * 2014-06-27
 */
class ScalaWhileUnwrapper extends ScalaUnwrapper {
  override def getDescription(e: PsiElement) = CodeInsightBundle.message("unwrap.while")

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]) = e match {
    case _: ScWhileStmt | _: ScDoStmt =>
      super.collectAffectedElements(e, toExtract)
      e
    case _ => e
  }

  override def isApplicableTo(e: PsiElement) = e match {
    case _: ScWhileStmt | _: ScDoStmt => true
    case _ => false
  }

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext) = element match {
    case ScWhileStmt(_, Some(body)) =>
      context.extractBlockOrSingleStatement(body, element)
      context.delete(element)
    case ScDoStmt(Some(body), _) =>
      context.extractBlockOrSingleStatement(body, element)
      context.delete(element)
    case _ =>
  }
}
