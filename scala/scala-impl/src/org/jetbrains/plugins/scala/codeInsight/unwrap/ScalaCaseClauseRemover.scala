package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}

import java.util

class ScalaCaseClauseRemover extends ScalaUnwrapper {

  override def isApplicableTo(e: PsiElement): Boolean = forCaseClause(e)(_ => true)(false)

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext): Unit =
    forCaseClause(element)(context.delete(_)) {}

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]): PsiElement =
    forCaseClause[PsiElement](e){ cl =>
      super.collectAffectedElements(cl, toExtract)
      cl
    } (e)

  private def forCaseClause[T](e: PsiElement)(ifClause: (ScCaseClause) => T)(ifNot: => T): T = {
    e match {
      case (cl: ScCaseClause) childOf (cls: ScCaseClauses) if cls.caseClauses.size > 1 => ifClause(cl)
      case _ => ifNot
    }
  }

  override def getDescription(e: PsiElement): String = ScalaBundle.message("remove.case.clause")
}
