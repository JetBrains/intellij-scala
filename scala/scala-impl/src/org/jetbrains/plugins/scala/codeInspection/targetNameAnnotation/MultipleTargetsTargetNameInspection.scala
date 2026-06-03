package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import com.intellij.codeInspection._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCases, ScValueOrVariableDefinition}

class MultipleTargetsTargetNameInspection extends TargetNameInspectionBase {

  import MultipleTargetsTargetNameInspection._

  override protected val findProblemElement: PartialFunction[PsiElement, ProblemElement] = {
    case TargetNameAnnotationWithOwner(annotation, annotationOwner@MultipleTargets()) =>
      val quickFix = new RemoveAnnotationQuickFix(annotation, annotationOwner)
      ProblemElement(annotation, quickFix, message)
  }
}

object MultipleTargetsTargetNameInspection {
  private[targetNameAnnotation] val message = ScalaInspectionBundle.message("targetname.multiple.targets")

  private object MultipleTargets {
    def unapply(element: PsiElement): Boolean = element match {
      case valOrVar: ScValueOrVariableDefinition =>
        valOrVar.bindings.sizeIs > 1
      case enumCases: ScEnumCases =>
        enumCases.declaredElements.sizeIs > 1
      case _ => false
    }
  }
}
