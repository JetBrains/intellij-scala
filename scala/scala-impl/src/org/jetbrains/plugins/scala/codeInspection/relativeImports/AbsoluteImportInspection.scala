package org.jetbrains.plugins.scala
package codeInspection.relativeImports

import codeInspection.relativeImports.AbsoluteImportInspection.OptimizeImportsQuickFix
import codeInspection.{AbstractInspection, ScalaInspectionBundle}
import editor.importOptimizer.ScalaImportOptimizer
import extensions.ObjectExt
import lang.formatting.settings.ScalaCodeStyleSettings
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.imports.ScImportExpr
import settings.ScalaProjectSettings

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

import scala.annotation.nowarn

@nowarn("msg=" + AbstractInspection.DeprecationText)
class AbsoluteImportInspection extends AbstractInspection(ScalaInspectionBundle.message("display.name.absolute.import")) {
  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case importExpr: ScImportExpr if importExpr.qualifier != null =>

      val project = importExpr.getProject
      val qualifier = importExpr.qualifier

      if (ScalaCodeStyleSettings.getInstance(project).isAddImportsRelativeToBasePackage) {
        qualifier.module.map(ScalaProjectSettings.getInstance(project).getBasePackageFor).filterNot(_.isEmpty).foreach { basePackage =>
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
