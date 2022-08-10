package org.jetbrains.plugins.scala
package codeInspection
package syntacticSimplification

import com.intellij.codeInspection._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.quickfix.RemoveReturnKeywordQuickFix
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReturn
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExpressionExt, ScFunction, ScFunctionDefinition}

import scala.annotation.nowarn

@nowarn("msg=" + AbstractInspection.DeprecationText)
class RemoveRedundantReturnInspection extends AbstractInspection() {

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
