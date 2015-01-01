package org.jetbrains.plugins.scala
package codeInspection
package redundantReturnInspection

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReturnStmt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}


class RemoveRedundantReturnInspection extends AbstractInspection("ScalaRedundantReturn", "Redundant Return") {

  def actionFor(holder: ProblemsHolder) = {
    case function: ScFunctionDefinition =>
    for (body <- function.body) {
        val returns = body.calculateReturns()
        body.depthFirst(!_.isInstanceOf[ScFunction]).foreach {
          case r: ScReturnStmt =>
            if (returns.contains(r)) {
              holder.registerProblem(r.returnKeyword, "Return keyword is redundant",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveReturnKeywordQuickFix(r))
            }
          case _ =>
        }
    }
  }
}

class RemoveReturnKeywordQuickFix(r: ScReturnStmt)
        extends AbstractFixOnPsiElement(ScalaBundle.message("remove.return.keyword"), r) {
  def doApplyFix(project: Project) {
    val ret = getElement
    if (!ret.isValid) return
    ret.expr match {
      case Some(e) => ret.replace(e.copy())
      case None => ret.delete()
    }
  }
}