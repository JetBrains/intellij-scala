package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScIf}

import java.util

class ScalaElseRemover extends ScalaElseUnwrapperBase {
  override protected def unwrapElseBranch(expr: ScExpression, ifStmt: ScIf, context: ScalaUnwrapContext): Unit = {
    expr.getParent match {
      case ifSt @ ScIf(_, Some(`expr`), Some(elseExpr)) childOf (parentIf @ ScIf(_, _, Some(elseIf))) if ifSt == elseIf =>
        context.setElseBranch(parentIf, elseExpr)
      case _ =>
        context.delete(ifStmt.findFirstChildByType(ScalaTokenTypes.kELSE).get)
        context.delete(expr)
    }
  }

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]): PsiElement = elseBranch(e) match {
    case Some((_, ifStmt: ScIf)) if ifStmt.thenExpression.isDefined =>
      super.collectAffectedElements(e, toExtract)
      ifStmt.thenExpression.get
    case Some((_, expr)) =>
      super.collectAffectedElements(e, toExtract)
      expr
    case _ => e
  }

  override def getDescription(e: PsiElement): String = CodeInsightBundle.message("remove.else")
}

