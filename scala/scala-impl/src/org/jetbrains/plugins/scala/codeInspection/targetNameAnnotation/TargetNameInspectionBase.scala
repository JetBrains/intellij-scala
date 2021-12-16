package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractRegisteredInspection
import org.jetbrains.plugins.scala.extensions.PsiElementExt

trait TargetNameInspectionBase extends AbstractRegisteredInspection {
  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager,
                                           isOnTheFly: Boolean): Option[ProblemDescriptor] =
    if (element.isInScala3File)
      findProblemElement.lift(element).flatMap { case ProblemElement(elem, quickFix, description) =>
        //noinspection ReferencePassedToNls
        super.problemDescriptor(elem, quickFix, description.getOrElse(descriptionTemplate), highlightType)
      }
    else None

  protected def findProblemElement: PartialFunction[PsiElement, ProblemElement]
}
