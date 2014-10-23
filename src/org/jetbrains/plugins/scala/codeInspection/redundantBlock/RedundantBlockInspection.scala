package org.jetbrains.plugins.scala
package codeInspection.redundantBlock

import com.intellij.codeInspection.{ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFix, AbstractInspection}
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * Pavel Fatin
 */

class RedundantBlockInspection extends AbstractInspection {

  def actionFor(holder: ProblemsHolder) = {
    case (block: ScBlock) childOf ((blockOfExpr: ScBlock) childOf (_: ScCaseClause))
      if block.hasRBrace && block.getFirstChild.getText == "{" &&
              blockOfExpr.getChildren.length == 1 && !block.getChildren.exists(_.isInstanceOf[ScCaseClauses]) =>
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
        val isRedundant = parent match {
          case _: ScArgumentExprList => false
          case _ if next == null => true
          case _: ScInterpolatedStringLiteral =>
            val text = child.getText
            val nextLetter = next.getText.headOption.getOrElse(' ')
            val checkId = ScalaNamesUtil.isIdentifier(text) && (nextLetter == '$' || !ScalaNamesUtil.isIdentifier(text + nextLetter))
            checkId && !text.startsWith("_") && !text.exists(_ == '$') && !text.startsWith("`")
          case _ => false
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
      val children = block.getChildren.drop(1).dropRight(1)
      for (child <- children) {
        block.getParent.addBefore(child, block)
      }
      block.delete()
    }
  }
}
