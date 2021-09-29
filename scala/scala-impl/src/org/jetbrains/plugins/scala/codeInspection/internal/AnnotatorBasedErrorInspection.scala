package org.jetbrains.plugins.scala
package codeInspection
package internal

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.{HighlightInfoHolder, HighlightVisitorImpl}
import com.intellij.codeInspection._
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation._
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiElementVisitor, PsiJavaFile}
import org.jetbrains.plugins.scala.annotator.{ScalaAnnotation, ScalaAnnotationHolder, ScalaAnnotator}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

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
        case scalaFile: ScalaFile if !InjectedLanguageManager.getInstance(project).isInjectedFragment(scalaFile) => // todo: remove this after proper support of scala fragments in .md files
          val annotator = new ScalaAnnotator() {
            override def isAdvancedHighlightingEnabled(element: PsiElement): Boolean = true
          }
          annotator.annotate(element)(new DummyAnnotationHolder(element, holder))
        case _ =>
      }
    }
  }
}

object AnnotatorBasedErrorInspection {

  import ProblemHighlightType.{ERROR, GENERIC_ERROR_OR_WARNING}

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

  private class DummyAnnotationHolder(element: PsiElement, holder: ProblemsHolder) extends ScalaAnnotationHolder {

    override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String,
                                  htmlTooltip: String): ScalaAnnotation = ScalaAnnotation.Empty

    override def createAnnotation(severity: HighlightSeverity, range: TextRange, str: String): ScalaAnnotation = ScalaAnnotation.Empty

    override def isBatchMode: Boolean = false

    override def createInfoAnnotation(range: TextRange, message: String): ScalaAnnotation = ScalaAnnotation.Empty

    override def createInfoAnnotation(node: ASTNode, message: String): ScalaAnnotation = ScalaAnnotation.Empty

    override def createInfoAnnotation(elt: PsiElement, message: String): ScalaAnnotation = ScalaAnnotation.Empty

    def createInformationAnnotation(range: TextRange, message: String): ScalaAnnotation = ScalaAnnotation.Empty

    def createInformationAnnotation(node: ASTNode, message: String): ScalaAnnotation = ScalaAnnotation.Empty

    def createInformationAnnotation(elt: PsiElement, message: String): ScalaAnnotation = ScalaAnnotation.Empty

    override def createWarningAnnotation(range: TextRange, message: String): ScalaAnnotation = {
      holder.registerProblem(element, ScalaInspectionBundle.message("warning.with.message", message), GENERIC_ERROR_OR_WARNING)
      ScalaAnnotation.Empty
    }

    override def createWarningAnnotation(node: ASTNode, message: String): ScalaAnnotation = {
      holder.registerProblem(element, ScalaInspectionBundle.message("warning.with.message", message), GENERIC_ERROR_OR_WARNING)
      ScalaAnnotation.Empty
    }

    override def createWarningAnnotation(elt: PsiElement, message: String): ScalaAnnotation = {
      holder.registerProblem(element, ScalaInspectionBundle.message("warning.with.message", message), GENERIC_ERROR_OR_WARNING)
      ScalaAnnotation.Empty
    }

    override def createErrorAnnotation(range: TextRange, message: String): ScalaAnnotation = {
      if (message != null) {
        holder.registerProblem(element, ScalaInspectionBundle.message("error.detected.with.message", message), ERROR)
      }
      ScalaAnnotation.Empty
    }

    override def createErrorAnnotation(node: ASTNode, message: String): ScalaAnnotation = {
      if (message != null) {
        holder.registerProblem(element, ScalaInspectionBundle.message("error.detected.with.message", message), ERROR)
      }
      ScalaAnnotation.Empty
    }

    override def createErrorAnnotation(elt: PsiElement, message: String): ScalaAnnotation = {
      if (message != null) {
        holder.registerProblem(element, ScalaInspectionBundle.message("error.detected.with.message", message), ERROR)
      }
      ScalaAnnotation.Empty
    }

    override def getCurrentAnnotationSession: AnnotationSession = {
      new AnnotationSession(element.getContainingFile)
    }

    override def createWeakWarningAnnotation(p1: TextRange, p2: String): ScalaAnnotation = ScalaAnnotation.Empty

    override def createWeakWarningAnnotation(p1: ASTNode, p2: String): ScalaAnnotation = ScalaAnnotation.Empty

    override def createWeakWarningAnnotation(p1: PsiElement, p2: String): ScalaAnnotation = ScalaAnnotation.Empty
  }

}