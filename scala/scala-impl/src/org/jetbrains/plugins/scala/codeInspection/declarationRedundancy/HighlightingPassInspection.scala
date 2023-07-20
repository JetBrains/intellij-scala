package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemsHolder}
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.annotations.Nls

trait HighlightingPassInspection extends LocalInspectionTool {
  override final def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    if (isOnTheFly)
      PsiElementVisitor.EMPTY_VISITOR //highlighting pass should take care of that
    else {
      //REMINDER: this is for the case when inspection are run in batch mode
      new PsiElementVisitor {
        override def visitElement(element: PsiElement): Unit = if (shouldProcessElement(element)) {
          invoke(element, isOnTheFly).foreach { info =>
            holder.registerProblem(info.element, info.message, info.fixes: _*)
          }
        }
      }
    }
  }

  def invoke(element: PsiElement, isOnTheFly: Boolean): Seq[ProblemInfo]

  def shouldProcessElement(elem: PsiElement): Boolean
}

case class ProblemInfo(element: PsiElement,
                       @Nls message: String,
                       fixes: Seq[LocalQuickFix])
