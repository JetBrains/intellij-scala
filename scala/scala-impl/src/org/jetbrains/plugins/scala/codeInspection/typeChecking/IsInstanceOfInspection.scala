package org.jetbrains.plugins.scala.codeInspection.typeChecking

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractRegisteredInspection, ScalaInspectionBundle}

class IsInstanceOfInspection extends AbstractRegisteredInspection {
  override protected def problemDescriptor(
    element: PsiElement,
    maybeQuickFix: Option[LocalQuickFix],
    descriptionTemplate: String,
    highlightType: ProblemHighlightType
  )(implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {

    element match {
      case IsInstanceOfCall.withoutExplicitType() =>
        val message = ScalaInspectionBundle.message("missing.explicit.type.in.isinstanceof.call")
        super.problemDescriptor(element, None, message, highlightType)
      case _ => None
    }
  }
}
