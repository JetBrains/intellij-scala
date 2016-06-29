package org.jetbrains.plugins.hocon.ref

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.hocon.psi.HIncludeTarget

class HoconIncludeResolutionInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
    new PsiElementVisitor {
      override def visitElement(element: PsiElement): Unit = element match {
        case hit: HIncludeTarget =>
          hit.getFileReferences.foreach { ref =>
            if (!ref.isSoft && ref.multiResolve(false).isEmpty) {
              holder.registerProblem(ref, ProblemsHolder.unresolvedReferenceMessage(ref),
                ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
            }
          }
        case _ =>
          super.visitElement(element)
      }
    }
}
