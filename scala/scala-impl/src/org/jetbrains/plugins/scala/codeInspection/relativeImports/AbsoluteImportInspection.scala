package org.jetbrains.plugins.scala.codeInspection.relativeImports

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.codeInspection.relativeImports.AbsoluteImportInspection.OptimizeImportsQuickFix
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.editor.importOptimizer.{OptimizeImportSettings, ScalaImportOptimizer}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr

import scala.annotation.unused

@unused("registered in scala-plugin-common.xml")
class AbsoluteImportInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case importExpr@ScImportExpr.qualifier(qualifier) =>

      importExpr.containingFile.foreach { file =>
        OptimizeImportSettings(file).basePackage.foreach { basePackage =>
          if ((qualifier.getText + ".").startsWith(basePackage + ".")) {
            holder.registerProblem(importExpr, ScalaInspectionBundle.message("absolute.import.detected"),
              ProblemHighlightType.LIKE_UNUSED_SYMBOL, TextRange.create(0, basePackage.length + 1), new OptimizeImportsQuickFix())
          }
        }
      }
    case _ =>
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

    override def generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
      IntentionPreviewInfo.EMPTY
  }
}
