package org.jetbrains.plugins.scala
package codeInspection
package syntacticSimplification

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReturn
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExpressionExt, ScFunction, ScFunctionDefinition}

class RemoveRedundantReturnInspection extends AbstractInspection(ScalaInspectionBundle.message("display.name.redundant.return")) {

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Unit] = {
    case function: ScFunctionDefinition =>
      for (body <- function.body) {
        val returns = body.calculateTailReturns
        body.depthFirst {
          !_.isInstanceOf[ScFunction]
        }.foreach {
          case r: ScReturn =>
            if (returns.contains(r)) {
              holder.registerProblem(
                r.keyword,
                ScalaInspectionBundle.message("return.keyword.is.redundant"),
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                new RemoveReturnKeywordQuickFix(r)
              )
            }
          case _ =>
        }
      }
  }
}

class RemoveReturnKeywordQuickFix(r: ScReturn)
  extends AbstractFixOnPsiElement(ScalaBundle.message("remove.return.keyword"), r) {

  override protected def doApplyFix(ret: ScReturn)
                                   (implicit project: Project): Unit = {
    ret.expr match {
      case Some(e) => ret.replace(e.copy())
      case None => ret.delete()
    }
  }
}