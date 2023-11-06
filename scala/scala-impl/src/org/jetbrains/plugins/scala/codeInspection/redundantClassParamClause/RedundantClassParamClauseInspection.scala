package org.jetbrains.plugins.scala.codeInspection.redundantClassParamClause

import com.intellij.codeInspection.ProblemHighlightType.LIKE_UNUSED_SYMBOL
import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

class RedundantClassParamClauseInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case c: ScClass if !c.isCase =>
      for {
        paramClauses <- c.clauses
        if paramClauses.isEmpty && paramClauses.getTextLength > 0
      } {
        holder.registerProblem(
          paramClauses,
          ScalaInspectionBundle.message("empty.parameter.clause.is.redundant"),
          LIKE_UNUSED_SYMBOL,
          new RemoveRedundantClassParamClause(paramClauses)
        )
      }
    case _ =>
  }
}
