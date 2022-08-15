package org.jetbrains.plugins.scala.codeInspection.recursion

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.annotator.FunctionAnnotator
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, intention}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

final class NoTailRecursionAnnotationInspection extends LocalInspectionTool {

  import intention.recursion.AddTailRecursionAnnotationIntention._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case element@CanBeTailRecursive(function) if FunctionAnnotator.canBeTailRecursive(function) =>
      val quickFix = new AbstractFixOnPsiElement(
        ScalaCodeInsightBundle.message("no.tailrec.annotation.fix"),
        function
      ) {
        override protected def doApplyFix(function: ScFunctionDefinition)
                                         (implicit project: Project): Unit = {
          addTailRecursionAnnotation(function)
        }
      }

      holder.registerProblem(element, getDisplayName, quickFix)
    case _ => None
  }
}
