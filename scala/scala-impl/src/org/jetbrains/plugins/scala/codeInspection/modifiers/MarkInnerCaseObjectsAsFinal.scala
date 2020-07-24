package org.jetbrains.plugins.scala
package codeInspection
package modifiers

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

class MarkInnerCaseObjectsAsFinal extends AbstractRegisteredInspection {
  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager,
                                           isOnTheFly: Boolean): Option[ProblemDescriptor] =
    element match {
      case obj@ScObject.withModifierList(ml) if !obj.isTopLevel && !ml.isFinal && ml.isCase =>
        val quickFix = new SetModifierQuickfix(obj, ScalaModifier.Final, set = true)
        super.problemDescriptor(obj.targetToken, Some(quickFix), descriptionTemplate, highlightType)
      case _ => None
    }
}
