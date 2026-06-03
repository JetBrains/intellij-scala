package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import com.intellij.codeInspection._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle

class MultipleTargetNameAnnotationsInspection extends TargetNameInspectionBase {
  override protected val findProblemElement: PartialFunction[PsiElement, ProblemElement] = {
    case TargetNameAnnotationWithOwner(annotation, owner) if owner.annotations(TargetNameAnnotationFQN).sizeIs > 1 =>
      val quickFix = new RemoveAnnotationQuickFix(annotation, owner)
      ProblemElement(annotation, quickFix, MultipleTargetNameAnnotationsInspection.message)
  }
}

object MultipleTargetNameAnnotationsInspection {
  private[targetNameAnnotation] val message =
    ScalaInspectionBundle.message("all.but.last.targetname.annotation.ignored")
}
