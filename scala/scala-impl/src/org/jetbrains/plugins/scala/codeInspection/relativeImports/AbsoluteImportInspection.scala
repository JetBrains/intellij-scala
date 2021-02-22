package org.jetbrains.plugins.scala
package codeInspection.relativeImports

import codeInspection.relativeImports.AbsoluteImportInspection.OptimizeImportsQuickFix
import codeInspection.{AbstractInspection, ScalaInspectionBundle}
import editor.importOptimizer.{OptimizeImportSettings, ScalaImportOptimizer}
import extensions.{ObjectExt, PsiElementExt}
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.imports.ScImportExpr

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

import scala.annotation.nowarn

@nowarn("msg=" + AbstractInspection.DeprecationText)
class AbsoluteImportInspection extends AbstractInspection(ScalaInspectionBundle.message("display.name.absolute.import")) {
  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case importExpr@ScImportExpr.qualifier(qualifier) =>

      importExpr.containingFile.foreach { file =>
        OptimizeImportSettings(file).basePackage.foreach { basePackage =>
          if ((qualifier.getText + ".").startsWith(basePackage + ".")) {
            holder.registerProblem(qualifier, ScalaInspectionBundle.message("absolute.import.detected"),
              ProblemHighlightType.LIKE_UNUSED_SYMBOL, TextRange.create(0, basePackage.length + 1), new OptimizeImportsQuickFix())
          }
        }
      }
  }
}

private object AbsoluteImportInspection {
  private class OptimizeImportsQuickFix extends LocalQuickFix {
    override def getFamilyName: String = QuickFixBundle.message("optimize.imports.fix")

    override def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
      descriptor.getPsiElement.getContainingFile.asOptionOf[ScalaFile].foreach { file =>
        ScalaImportOptimizer.findOptimizerFor(file).foreach { optimizer =>
          optimizer.processFile(file).run()
        }
      }
    }
  }
}
