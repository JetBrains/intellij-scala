package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScIf}

import java.util

class ScalaElseUnwrapper extends ScalaElseUnwrapperBase {
  override protected def unwrapElseBranch(expr: ScExpression, ifStmt: ScIf, context: ScalaUnwrapContext): Unit = {
    val from = maxIfStmt(ifStmt)
    val branch = expr match {
      case ScIf(_, Some(thenBr), _) => thenBr
      case _ => expr
    }
    context.extractBlockOrSingleStatement(branch, from)
    context.delete(from)
  }

  override def getDescription(e: PsiElement): String = CodeInsightBundle.message("unwrap.else")

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]): PsiElement = elseBranch(e) match {
    case Some((ifStmt: ScIf, _)) =>
      super.collectAffectedElements(e, toExtract)
      maxIfStmt(ifStmt)
    case _ => e
  }
}
