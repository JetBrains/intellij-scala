package org.jetbrains.plugins.scala
package codeInsight.unwrap

import java.util

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScIfStmt}

/**
 * Nikolay.Tropin
 * 2014-06-27
 */
class ScalaElseRemover extends ScalaElseUnwrapperBase {
  override protected def unwrapElseBranch(expr: ScExpression, ifStmt: ScIfStmt, context: ScalaUnwrapContext) = {
    expr.getParent match {
      case ifSt @ ScIfStmt(_, Some(`expr`), Some(elseExpr)) childOf (parentIf @ ScIfStmt(_, _, Some(elseIf))) if ifSt == elseIf =>
        context.setElseBranch(parentIf, elseExpr)
      case _ =>
        context.delete(ifStmt.findFirstChildByType(ScalaTokenTypes.kELSE))
        context.delete(expr)
    }
  }

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]) = elseBranch(e) match {
    case Some((_, ifStmt: ScIfStmt)) if ifStmt.thenBranch.isDefined =>
      super.collectAffectedElements(e, toExtract)
      ifStmt.thenBranch.get
    case Some((_, expr)) =>
      super.collectAffectedElements(e, toExtract)
      expr
    case _ => e
  }

  override def getDescription(e: PsiElement) = CodeInsightBundle.message("remove.else")
}

