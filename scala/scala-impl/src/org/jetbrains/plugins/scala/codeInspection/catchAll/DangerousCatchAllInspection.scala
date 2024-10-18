package org.jetbrains.plugins.scala.codeInspection.catchAll

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScReferencePattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScCatchBlock}

final class DangerousCatchAllInspection extends LocalInspectionTool with DumbAware {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitCatchBlock(catchBlock: ScCatchBlock): Unit = {
        catchBlock.expression match {
          case Some(block: ScBlockExpr) =>
            for {
              caseClauses <- block.caseClauses
              if caseClauses.caseClauses.sizeIs == 1
              caseClause <- caseClauses.caseClauses.headOption
              pattern <- caseClause.pattern
              if pattern.is[ScWildcardPattern, ScReferencePattern] && caseClause.guard.isEmpty
            } holder.registerProblem(holder.getManager.createProblemDescriptor(caseClause.getFirstChild, pattern,
              ScalaInspectionBundle.message("catch.all"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly,
              new ReplaceDangerousCatchAllQuickFix(caseClause)))
          case _ =>
        }
      }
    }
  }
}
