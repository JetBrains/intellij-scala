package org.jetbrains.plugins.scala
package codeInspection
package imports

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelectors}

class SingleImportInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitImportExpr(importExpr: ScImportExpr): Unit = {
        importExpr.selectorSet.foreach {
          case selectorSet@ScImportSelectors(selector) if selectorSet.getFirstChild.elementType == ScalaTokenTypes.tLBRACE =>
            //Scala 2 alias requires braces: `import scala.util.{Random => Random}`
            //Scala 3 alias can go without braces: `import scala.util.Random as Random2`
            if (!selector.isScala2StyleAliasImport) {
              holder.registerProblem(holder.getManager.createProblemDescriptor(
                selectorSet,
                ScalaInspectionBundle.message("single.import"),
                new RemoveBracesForSingleImportQuickFix(importExpr),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)
              )
            }
          case _ =>
        }
      }
    }
  }
}

private class RemoveBracesForSingleImportQuickFix(importExpr: ScImportExpr)
  extends AbstractFixOnPsiElement(ScalaBundle.message("remove.braces.from.import"), importExpr) {

  override protected def doApplyFix(iExpr: ScImportExpr)
                                   (implicit project: Project): Unit = {
    inWriteAction {
      importExpr.deleteRedundantSingleSelectorBraces()
    }
  }
}

