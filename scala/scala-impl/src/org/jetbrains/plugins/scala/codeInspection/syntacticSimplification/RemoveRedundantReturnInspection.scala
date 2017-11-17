package org.jetbrains.plugins.scala
package codeInspection
package syntacticSimplification

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.calculateReturns
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReturnStmt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}

class RemoveRedundantReturnInspection extends AbstractInspection("ScalaRedundantReturn", "Redundant Return") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case function: ScFunctionDefinition =>
    for (body <- function.body) {
      val returns = calculateReturns(body)
      body.depthFirst {
        !_.isInstanceOf[ScFunction]
      }.foreach {
          case r: ScReturnStmt =>
            if (returns(r)) {
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


  override protected def doApplyFix(ret: ScReturnStmt)
                                   (implicit project: Project): Unit = {
    ret.expr match {
      case Some(e) => ret.replace(e.copy())
      case None => ret.delete()
    }
  }
}