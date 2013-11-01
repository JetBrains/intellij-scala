package org.jetbrains.plugins.scala
package codeInspection.redundantBlock

import com.intellij.codeInspection.{ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import codeInspection.{AbstractFix, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral

/**
 * Pavel Fatin
 */

class RedundantBlockInspection extends AbstractInspection {

  def actionFor(holder: ProblemsHolder) = {
    case (block: ScBlock) childOf ((blockOfExpr: ScBlock) childOf (_: ScCaseClause))
      if blockOfExpr.getChildren.length == 1 && block.hasRBrace && block.getFirstChild.getText == "{" =>
      holder.registerProblem(block, new TextRange(0, 1), "Remove redundant braces", new InCaseClauseQuickFix(block))
    case block: ScBlockExpr if block.getChildren.length == 3 =>
      val child: PsiElement = block.getChildren.apply(1)
      val probablyRedundant = child match {
        case ref: ScReferenceExpression if ref.qualifier.isEmpty => true
        case t: ScThisReference if t.reference.isEmpty => true
        case _ => false
      }
      if (probablyRedundant) {
        val next: PsiElement = block.getNextSibling
        val parent = block.getParent
        val isRedundant =
        if (parent.isInstanceOf[ScArgumentExprList]) false
        else if (next == null) true
        else if (parent.isInstanceOf[ScInterpolatedStringLiteral] && child.getText.startsWith("_")) false //SCL-6124
        else {
            val refName: String = child.getText + (if (next.getText.length > 0) next.getText charAt 0 else "")
            !ScalaNamesUtil.isIdentifier(refName) && !refName.exists(_ == '$')
          }
        if (isRedundant) {
          holder.registerProblem(block, "The enclosing block is redundant", new QuickFix(block))
        }
      }
  }

  private class QuickFix(e: PsiElement) extends AbstractFix("Unwrap the expression", e) {
    def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
      e.replace(e.getChildren.apply(1))
    }
  }
  
  private class InCaseClauseQuickFix(block: ScBlock) extends AbstractFix("Remove redundant braces", block) {
    def doApplyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
      val stmts = block.statements
      for (stmt <- stmts) {
        block.getParent.addBefore(stmt, block)
      }
      block.delete()
    }
  }
}
