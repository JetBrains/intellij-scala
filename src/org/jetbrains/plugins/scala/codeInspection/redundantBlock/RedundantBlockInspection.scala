package org.jetbrains.plugins.scala
package codeInspection.redundantBlock

import com.intellij.codeInspection.{ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import codeInspection.{AbstractFix, AbstractInspection}
import lang.psi.api.expr.ScBlockExpr
import extensions._

/**
 * Pavel Fatin
 */

class RedundantBlockInspection extends AbstractInspection {
  private val SimpleBlockPattern = "\\{[A-z]+\\}".r

  def actionFor(holder: ProblemsHolder) = {
    case block: ScBlockExpr if block.getChildren.length == 3 && SimpleBlockPattern.matches(block.getText) =>
      holder.registerProblem(block, "The enclosing block is redundant", new QuickFix(block))
  }

  private class QuickFix(e: PsiElement) extends AbstractFix("Unwrap the expression", e) {
    def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
      e.replace(e.getChildren.apply(1))
    }
  }
}
