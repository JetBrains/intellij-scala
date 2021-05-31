package org.jetbrains.plugins.scala
package codeInspection
package imports

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

/**
 * @author Ksenia.Sautina
 * @since 4/10/12
 */

class SingleImportInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitImportExpr(importExpr: ScImportExpr): Unit = {
        if (importExpr.selectorSet.isDefined && importExpr.selectors.length == 1) {
          if (importExpr.selectors.length == 1 &&
            !importExpr.isScala3OrSource3Enabled &&
            importExpr.selectors.head.isAliasedImport) {
            return
          }
          holder.registerProblem(holder.getManager.createProblemDescriptor(importExpr.selectorSet.get,
            ScalaInspectionBundle.message("single.import"),
            new RemoveBracesForSingleImportQuickFix(importExpr),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly))
        }
      }
    }
  }
}

