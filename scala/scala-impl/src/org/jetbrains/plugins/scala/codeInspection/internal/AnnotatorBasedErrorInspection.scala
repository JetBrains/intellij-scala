package org.jetbrains.plugins.scala
package codeInspection
package internal

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.{HighlightInfoHolder, HighlightVisitorImpl}
import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiElementVisitor, PsiJavaFile}

/**
 * @author Alexander Podkhalyuzin
 */
final class AnnotatorBasedErrorInspection extends LocalInspectionTool {

  import AnnotatorBasedErrorInspection._

  //noinspection TypeAnnotation
  override def buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean) = new PsiElementVisitor {

    override def visitElement(element: PsiElement): Unit = {
      implicit val project: Project = element.getProject

      element.getContainingFile match {
        case javaFile: PsiJavaFile => highlightJavaElement(element, javaFile, holder)
        case _ =>
      }
    }
  }
}

object AnnotatorBasedErrorInspection {

  import ProblemHighlightType.ERROR

  private def highlightJavaElement(element: PsiElement,
                                   javaFile: PsiJavaFile,
                                   holder: ProblemsHolder)
                                  (implicit project: Project): Unit = {
    val highlightInfoHolder = new HighlightInfoHolder(javaFile)

    for {
      visitor <- HighlightVisitor.EP_HIGHLIGHT_VISITOR
        .getExtensions(project)
        .headOption

      if visitor.isInstanceOf[HighlightVisitorImpl]
      cloned = visitor.asInstanceOf[HighlightVisitorImpl].clone
    } cloned.analyze(
      javaFile,
      true,
      highlightInfoHolder,
      () => visitor.visit(element)
    )

    if (highlightInfoHolder.hasErrorResults) {
      holder.registerProblem(
        element,
        ScalaInspectionBundle.message("error.detected"),
        ERROR,
        null: TextRange
      )
    }
  }
}
