package org.jetbrains.plugins.scala
package codeInspection.imports

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr

/**
 * @author Ksenia.Sautina
 * @since 4/10/12
 */

class SingleImportInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitImportExpr(importExpr: ScImportExpr) {
        if (importExpr.selectorSet.isDefined && importExpr.selectors.length + (if (importExpr.isSingleWildcard) 1 else 0) == 1) {
          if (importExpr.selectors.length == 1 && importExpr.selectors.head.isAliasedImport) {
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

