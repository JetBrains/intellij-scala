package org.jetbrains.plugins.scala
package codeInsight.unwrap

import java.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMatchStmt

/**
 * Nikolay.Tropin
 * 2014-06-27
 */
class ScalaMatchUnwrapper extends ScalaUnwrapper {
  
  override def isApplicableTo(e: PsiElement) = forCaseClauseInMatch(e)((_, _) => true)(false)

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext) = forCaseClauseInMatch(element) { (cl, m) =>
    context.extractBlockOrSingleStatement(cl.expr.get, m)
    context.delete(m)
  }()

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]) = forCaseClauseInMatch[PsiElement](e) { (cl, m) =>
    super.collectAffectedElements(e, toExtract)
    m
  }(e)

  override def getDescription(e: PsiElement) = ScalaBundle.message("unwrap.case.clause")

  private def forCaseClauseInMatch[T](e: PsiElement)(ifInClause: (ScCaseClause, ScMatchStmt) => T)(ifNot: => T): T = {
    e match {
      case (cl: ScCaseClause) childOf ((_: ScCaseClauses) childOf (matchStmt: ScMatchStmt)) if cl.expr.nonEmpty =>
        ifInClause(cl, matchStmt)
      case _ =>
        ifNot
    }
  }

}
