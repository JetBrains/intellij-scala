package org.jetbrains.plugins.scala
package codeInspection.imports

import com.intellij.psi.PsiElementVisitor
import lang.psi.api.ScalaElementVisitor
import lang.psi.api.toplevel.imports.ScImportExpr
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder, LocalInspectionTool}
import codeInspection.InspectionBundle

/**
 * @author Ksenia.Sautina
 * @since 4/10/12
 */

class SingleImportInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitImportExpr(importExpr: ScImportExpr) {
        if (importExpr.selectors.length == 1 &&
                (importExpr.selectors(0).getFirstChild == importExpr.selectors(0).getLastChild)) {
          holder.registerProblem(holder.getManager.createProblemDescriptor(importExpr.selectorSet.get,
            InspectionBundle.message("single.import"),
            new RemoveBracesForSingleImportQuickFix(importExpr),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly))
        }
      }
    }
  }
}

