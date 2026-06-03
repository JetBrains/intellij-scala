package org.jetbrains.plugins.scala.codeInspection.scaladoc

import com.intellij.codeInspection._
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.{PsiElementVisitor, PsiErrorElement}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.incremental.Highlighting._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

final class ScalaDocParserErrorInspection extends LocalInspectionTool with DumbAware {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    new ScalaElementVisitor {
      override def visitDocComment(s: ScDocComment): Unit =
        visitScaladocElement(s)

      override def visitScaladocElement(element: ScalaPsiElement): Unit = {
        if (!element.isVisible(holder.getProject, holder.getFile)) return

        element.getChildren.foreach {
          case errorElement: PsiErrorElement =>
            val markedElement =
              if (element.getTextLength > 0) element
              else element.nextLeafs.find(_.getTextLength > 0).get
            holder.registerProblem(
              holder.getManager.createProblemDescriptor(
                markedElement,
                errorElement.getErrorDescription,
                isOnTheFly,
                null,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
              )
            );
          case b: ScalaPsiElement if b.getChildren.nonEmpty => visitScaladocElement(b)
          case _ => //do nothing
        }
      }
    }
}
