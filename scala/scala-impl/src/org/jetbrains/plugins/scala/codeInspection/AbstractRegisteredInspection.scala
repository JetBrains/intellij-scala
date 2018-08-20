package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.codeInspection._
import com.intellij.psi.{PsiElement, PsiElementVisitor}

abstract class AbstractRegisteredInspection extends LocalInspectionTool {

  protected def problemDescriptor(element: PsiElement,
                                  maybeQuickFix: Option[LocalQuickFix] = None,
                                  descriptionTemplate: String = getDisplayName,
                                  highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                                 (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    val fixes = maybeQuickFix match {
      case Some(quickFix) => Array(quickFix)
      case _ => LocalQuickFix.EMPTY_ARRAY
    }
    val descriptor = manager.createProblemDescriptor(element, descriptionTemplate, isOnTheFly, fixes, highlightType)
    Some(descriptor)
  }

  override final def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = new PsiElementVisitor {

    override def visitElement(element: PsiElement): Unit =
      problemDescriptor(element)(holder.getManager, isOnTheFly) match {
        case Some(descriptor) => holder.registerProblem(descriptor)
        case _ =>
      }
  }

  /*
    * DO NOT OVERRIDE
    */
  override final def getDisplayName: String = super.getDisplayName
}
