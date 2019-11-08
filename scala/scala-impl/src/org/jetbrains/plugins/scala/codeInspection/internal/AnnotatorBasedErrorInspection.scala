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
      implicit val h: ProblemsHolder = holder

      element.getContainingFile match {
        case javaFile: PsiJavaFile => highlightJavaElement(element, javaFile)
        case scalaFile: ScalaFile if !InjectedLanguageManager.getInstance(project).isInjectedFragment(scalaFile) => // todo: remove this after proper support of scala fragments in .md files
          new annotator.ScalaAnnotator() {
            override def isAdvancedHighlightingEnabled(element: PsiElement): Boolean = true
          }.annotate(
            element,
            new DummyAnnotationHolder(element)
          )
        case _ =>
      }
    }
  }
}

object AnnotatorBasedErrorInspection {

  import ProblemHighlightType.{ERROR, GENERIC_ERROR_OR_WARNING}

  private def highlightJavaElement(element: PsiElement, javaFile: PsiJavaFile)
                                  (implicit project: Project,
                                   holder: ProblemsHolder): Unit = {
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
        "Error detected",
        ERROR,
        null: TextRange
      )
    }
  }

  private class DummyAnnotationHolder(element: PsiElement)
                                     (implicit holder: ProblemsHolder) extends AnnotationHolder {

    private val FakeAnnotation = new Annotation(
      0,
      0,
      HighlightSeverity.WEAK_WARNING,
      "message",
      "tooltip"
    )

    override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String,
                                  htmlTooltip: String): Annotation = FakeAnnotation

    def createAnnotation(severity: HighlightSeverity, range: TextRange, str: String): Annotation = FakeAnnotation

    def isBatchMode: Boolean = false

    def createInfoAnnotation(range: TextRange, message: String): Annotation = FakeAnnotation

    def createInfoAnnotation(node: ASTNode, message: String): Annotation = FakeAnnotation

    def createInfoAnnotation(elt: PsiElement, message: String): Annotation = FakeAnnotation

    def createInformationAnnotation(range: TextRange, message: String): Annotation = FakeAnnotation

    def createInformationAnnotation(node: ASTNode, message: String): Annotation = FakeAnnotation

    def createInformationAnnotation(elt: PsiElement, message: String): Annotation = FakeAnnotation

    def createWarningAnnotation(range: TextRange, message: String): Annotation = {
      holder.registerProblem(element, s"Warning: $message", GENERIC_ERROR_OR_WARNING)
      FakeAnnotation
    }

    def createWarningAnnotation(node: ASTNode, message: String): Annotation = {
      holder.registerProblem(element, s"Warning: $message", GENERIC_ERROR_OR_WARNING)
      FakeAnnotation
    }

    def createWarningAnnotation(elt: PsiElement, message: String): Annotation = {
      holder.registerProblem(element, s"Warning: $message", GENERIC_ERROR_OR_WARNING)
      FakeAnnotation
    }

    def createErrorAnnotation(range: TextRange, message: String): Annotation = {
      if (message != null) {
        holder.registerProblem(element, s"Error detected: $message", ERROR)
      }
      FakeAnnotation
    }

    def createErrorAnnotation(node: ASTNode, message: String): Annotation = {
      if (message != null) {
        holder.registerProblem(element, s"Error detected: $message", ERROR)
      }
      FakeAnnotation
    }

    def createErrorAnnotation(elt: PsiElement, message: String): Annotation = {
      if (message != null) {
        holder.registerProblem(element, s"Error detected: $message", ERROR)
      }
      FakeAnnotation
    }

    def getCurrentAnnotationSession: AnnotationSession = {
      new AnnotationSession(element.getContainingFile)
    }

    def createWeakWarningAnnotation(p1: TextRange, p2: String): Annotation = FakeAnnotation

    def createWeakWarningAnnotation(p1: ASTNode, p2: String): Annotation = FakeAnnotation

    def createWeakWarningAnnotation(p1: PsiElement, p2: String): Annotation = FakeAnnotation
  }

}