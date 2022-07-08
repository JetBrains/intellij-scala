package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScDo, ScWhile}

import java.util

class ScalaWhileUnwrapper extends ScalaUnwrapper {
  override def getDescription(e: PsiElement): String = CodeInsightBundle.message("unwrap.while")

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]): PsiElement = e match {
    case _: ScWhile | _: ScDo =>
      super.collectAffectedElements(e, toExtract)
      e
    case _ => e
  }

  override def isApplicableTo(e: PsiElement): Boolean = e match {
    case _: ScWhile | _: ScDo => true
    case _ => false
  }

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext): Unit = element match {
    case ScWhile(_, Some(body)) =>
      context.extractBlockOrSingleStatement(body, element)
      context.delete(element)
    case ScDo(Some(body), _) =>
      context.extractBlockOrSingleStatement(body, element)
      context.delete(element)
    case _ =>
  }
}
