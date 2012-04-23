package org.jetbrains.plugins.scala
package codeInspection.imports

import com.intellij.psi.PsiElementVisitor
import lang.psi.api.toplevel.imports.ScImportExpr
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder, LocalInspectionTool}
import codeInspection.InspectionBundle
import lang.psi.api.ScalaElementVisitor

/**
 * @author Ksenia.Sautina
 * @since 4/10/12
 */

class SingleImportInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitImportExpr(importExpr: ScImportExpr) {
        if (!importExpr.selectorSet.isEmpty && importExpr.selectors.length + (if (importExpr.singleWildcard) 1 else 0) == 1) {
          if (importExpr.selectors.length == 1 && importExpr.selectors(0).isAliasedImport) {
            return
          }
          holder.registerProblem(holder.getManager.createProblemDescriptor(importExpr.selectorSet.get,
            InspectionBundle.message("single.import"),
            new RemoveBracesForSingleImportQuickFix(importExpr),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly))
        }
      }
    }
  }
}

