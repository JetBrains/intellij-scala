package org.jetbrains.plugins.scala
package codeInspection.redundantBlock

import com.intellij.codeInspection.{ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import codeInspection.{AbstractFix, AbstractInspection}
import lang.psi.api.expr.{ScThisReference, ScReferenceExpression, ScBlockExpr}
import extensions._
import lang.refactoring.util.ScalaNamesUtil

/**
 * Pavel Fatin
 */

class RedundantBlockInspection extends AbstractInspection {

  def actionFor(holder: ProblemsHolder) = {
    case block: ScBlockExpr if block.getChildren.length == 3 =>
      val child: PsiElement = block.getChildren.apply(1)
      val probablyRedundant = child match {
        case ref: ScReferenceExpression if ref.qualifier.isEmpty => true
        case t: ScThisReference if t.reference.isEmpty => true
        case _ => false
      }
      if (probablyRedundant) {
        val next: PsiElement = block.getNextSibling
        val isRedundant =
          if (next == null) true
          else {
            val refName: String = child.getText + (if (next.getTextLength > 0) next.getText charAt 0 else "")
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
}
