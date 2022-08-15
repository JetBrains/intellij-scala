package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.PsiElementVisitorSimple
import org.jetbrains.plugins.scala.extensions.PsiElementExt

trait TargetNameInspectionBase extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = { element =>
    if (element.isInScala3File) {
      findProblemElement.lift(element).foreach { problemElement =>
        //noinspection ReferencePassedToNls
        holder.registerProblem(
          problemElement.element,
          problemElement.maybeDescription.getOrElse(getDisplayName),
          problemElement.maybeQuickFix.toArray: _*
        )
      }
    }
  }

  protected def findProblemElement: PartialFunction[PsiElement, ProblemElement]
}
