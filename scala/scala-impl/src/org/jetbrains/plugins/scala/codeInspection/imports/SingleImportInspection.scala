package org.jetbrains.plugins.scala.codeInspection.imports

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.incremental.Highlighting._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.parentheses.registerRedundantParensProblem
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelectors}

class SingleImportInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitImportExpr(importExpr: ScImportExpr): Unit = {
        if (!importExpr.isVisible(holder.getProject, holder.getFile)) return

        importExpr.selectorSet.foreach {
          case selectorSet@ScImportSelectors(selector)
            if selectorSet.getFirstChild.elementType == ScalaTokenTypes.tLBRACE &&
              selectorSet.getLastChild.elementType == ScalaTokenTypes.tRBRACE =>
            //Scala 2 alias requires braces: `import scala.util.{Random => Random}`
            //Scala 3 alias can go without braces: `import scala.util.Random as Random2`
            if (!selector.isScala2StyleAliasImport) {
              //highlight only the braces themselves, as "unused" (SCL-25732)
              registerRedundantParensProblem(
                ScalaInspectionBundle.message("single.import"),
                selectorSet,
                new RemoveBracesForSingleImportQuickFix(importExpr),
                holder,
                isOnTheFly
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

