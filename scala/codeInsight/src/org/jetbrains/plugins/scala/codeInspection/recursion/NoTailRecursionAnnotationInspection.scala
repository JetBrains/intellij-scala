package org.jetbrains.plugins.scala
package codeInspection
package recursion

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.FunctionAnnotator
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, intention}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

final class NoTailRecursionAnnotationInspection extends AbstractRegisteredInspection {

  import intention.recursion.AddTailRecursionAnnotationIntention._

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] =
    element match {
      case CanBeTailRecursive(function) if FunctionAnnotator.canBeTailRecursive(function) =>
        val quickFix = new AbstractFixOnPsiElement(
          ScalaCodeInsightBundle.message("no.tailrec.annotation.fix"),
          function
        ) {
          override protected def doApplyFix(function: ScFunctionDefinition)
                                           (implicit project: Project): Unit = {
            addTailRecursionAnnotation(function)
          }
        }

        super.problemDescriptor(element, Some(quickFix), descriptionTemplate, highlightType)
      case _ => None
    }
}
