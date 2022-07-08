package org.jetbrains.plugins.scala.codeInspection.catchAll

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScReferencePattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScCatchBlock}

class DangerousCatchAllInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitCatchBlock(catchBlock: ScCatchBlock): Unit = {
        val expr = catchBlock.expression.orNull
        if (expr == null) return
        def isInspection: (Boolean, ScCaseClause) = expr match {
          case block: ScBlockExpr =>
            val caseClauses = block.caseClauses.orNull
            if (caseClauses == null || caseClauses.caseClauses.size != 1) return (false, null)
            val caseClause = caseClauses.caseClause
            if (caseClause == null) return (false, null)
            val pattern = caseClause.pattern.orNull
            if (pattern == null) return (false, null)
            val guard = caseClause.guard.orNull
            pattern match {
              case _: ScWildcardPattern if guard == null => (true, caseClause)
              case _: ScReferencePattern if guard == null => (true, caseClause)
              case _ => (false, null)
            }
          case _ => (false, null)
        }
        if (isInspection._1) {
          val startElement = isInspection._2.getFirstChild
          val endElement = isInspection._2.pattern.orNull
          if (startElement == null || endElement == null) return
          holder.registerProblem(holder.getManager.createProblemDescriptor(startElement, endElement,
            ScalaInspectionBundle.message("catch.all"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly,
            new ReplaceDangerousCatchAllQuickFix(isInspection._2)))
        }
      }
    }
  }
}

