package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMatch

import java.util

class ScalaMatchUnwrapper extends ScalaUnwrapper {
  
  override def isApplicableTo(e: PsiElement): Boolean = forCaseClauseInMatch(e)((_, _) => true)(false)

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext): Unit = forCaseClauseInMatch(element) { (cl, m) =>
    context.extractBlockOrSingleStatement(cl.expr.get, m)
    context.delete(m)
  } {}

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]): PsiElement = forCaseClauseInMatch[PsiElement](e) { (_, m) =>
    super.collectAffectedElements(e, toExtract)
    m
  }(e)

  override def getDescription(e: PsiElement): String = ScalaBundle.message("unwrap.case.clause")

  private def forCaseClauseInMatch[T](e: PsiElement)(ifInClause: (ScCaseClause, ScMatch) => T)(ifNot: => T): T = {
    e match {
      case (cl: ScCaseClause) childOf ((_: ScCaseClauses) childOf (matchStmt: ScMatch)) if cl.expr.nonEmpty =>
        ifInClause(cl, matchStmt)
      case _ =>
        ifNot
    }
  }

}
