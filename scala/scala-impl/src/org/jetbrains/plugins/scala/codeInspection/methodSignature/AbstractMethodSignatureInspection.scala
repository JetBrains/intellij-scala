package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.codeInspection.PsiElementVisitorSimple
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

abstract class AbstractMethodSignatureInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = PsiElementVisitorSimple(holder) {
    case function: ScFunction if isApplicable(function) =>
      findProblemElement(function).foreach {
        holder.registerProblem(_, getDisplayName, highlightType(function), createQuickFix(function).toArray: _*)
      }
    case _ =>
  }

  protected def highlightType(function: ScFunction): ProblemHighlightType =
    ProblemHighlightType.GENERIC_ERROR_OR_WARNING

  protected def isApplicable(function: ScFunction): Boolean

  protected def findProblemElement(function: ScFunction): Option[PsiElement] = Some(function.nameId)

  protected def createQuickFix(function: ScFunction): Option[LocalQuickFix] = None
}
