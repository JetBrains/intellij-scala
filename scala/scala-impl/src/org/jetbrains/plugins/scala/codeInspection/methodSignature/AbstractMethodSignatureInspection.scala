package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

abstract class AbstractMethodSignatureInspection extends AbstractRegisteredInspection {

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager,
                                           isOnTheFly: Boolean): Option[ProblemDescriptor] =
    element match {
      case function: ScFunction if isApplicable(function) =>
        findProblemElement(function) match {
          case Some(problemElement) => super.problemDescriptor(problemElement, createQuickFix(function), descriptionTemplate, highlightType)
          case _ => None
        }
      case _ => None
    }

  protected def isApplicable(function: ScFunction): Boolean

  protected def findProblemElement(function: ScFunction): Option[PsiElement] = Some(function.nameId)

  protected def createQuickFix(function: ScFunction): Option[LocalQuickFix] = None
}