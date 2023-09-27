package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.codeInspection.quickfix.RemoveReturnKeywordQuickFix
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReturn
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExpressionExt, ScFunction, ScFunctionDefinition}

class RemoveRedundantReturnInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
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
                new RemoveReturnKeywordQuickFix(r)
              )
            }
          case _ =>
        }
      }
    case _ =>
  }
}
